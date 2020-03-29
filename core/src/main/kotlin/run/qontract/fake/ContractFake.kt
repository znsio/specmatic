package run.qontract.fake

import run.qontract.core.ContractBehaviour
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.utilities.contractGherkinForCurrentComponent
import run.qontract.core.utilities.getContractGherkin
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import run.qontract.core.pattern.parsedValue
import run.qontract.core.utilities.toMap
import run.qontract.core.value.NoValue
import run.qontract.core.value.Value
import java.io.Closeable
import java.util.*

internal suspend fun ktorHttpRequestToHttpRequest(call: ApplicationCall): HttpRequest {
    val(body, formFields) = bodyFromCall(call)

    return HttpRequest(method = call.request.httpMethod.value,
            path = call.request.path(),
            headers = HashMap(),
            body = body,
            queryParams = toParams(call.request.queryParameters),
            formFields = formFields)
}

private suspend fun bodyFromCall(call: ApplicationCall): Pair<Value, Map<String, String>> {
    return if (call.request.contentType().match(ContentType.Application.FormUrlEncoded))
        Pair(NoValue(), call.receiveParameters().toMap().mapValues { (_, values) -> values.first() })
    else
        Pair(parsedValue(call.receiveText()), emptyMap<String, String>())
}

internal fun toParams(queryParameters: Parameters) = HashMap(queryParameters.toMap().mapValues { it.value.first() })

internal fun respondToKtorHttpResponse(call: ApplicationCall, httpResponse: HttpResponse) {
    val textContent = TextContent(httpResponse.body as String, ContentType.Application.Json, HttpStatusCode.fromValue(httpResponse.status))

    try {
        val headersControlledByEngine = listOf("content-type", "content-length")
        for ((name, value) in httpResponse.headers.filterNot { it.key.toLowerCase() in headersControlledByEngine }) {
            call.response.headers.append(name, value ?: "")
        }

        runBlocking { call.respond(textContent) }
    }
    catch(e:Exception)
    {
        print(e.toString())
    }
}

class ContractFake(gherkinData: String, host: String, port: Int) : Closeable {
    val endPoint = "http://$host:$port"
    val contractBehaviour = ContractBehaviour(gherkinData)

    private val server: ApplicationEngine = embeddedServer(Netty, port) {
        intercept(ApplicationCallPipeline.Call) {
            val httpRequest = ktorHttpRequestToHttpRequest(call)

            if (isSetupRequest(httpRequest)) {
                setupServerState(httpRequest)
                call.response.status(HttpStatusCode.OK)
            } else {
                respondToKtorHttpResponse(call, contractBehaviour.lookup(httpRequest))
            }
        }
    }

    override fun close() {
        server.stop(0, 0)
    }

    companion object {
        @Throws(Throwable::class)
        fun forSupportedContract(): ContractFake {
            val gherkin = contractGherkinForCurrentComponent
            return ContractFake(gherkin, "localhost", 8080)
        }

        @JvmOverloads
        @Throws(Throwable::class)
        fun forService(serviceName: String?, host: String = "127.00.1", port: Int = 8080): ContractFake {
            val contractGherkin = getContractGherkin(serviceName!!)
            return ContractFake(contractGherkin, host, port)
        }
    }

    private fun setupServerState(httpRequest: HttpRequest) {
        val body = httpRequest.body
        val map = body?.let { toMap(it) } ?: mutableMapOf()
        contractBehaviour.setServerState(map.mapValues { it.value as Any })
    }

    private fun isSetupRequest(httpRequest: HttpRequest): Boolean {
        return httpRequest.path == "/_server_state" && httpRequest.method == "POST"
    }

    init {
        server.start()
    }
}