package run.qontract.fake

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import run.qontract.core.ContractBehaviour
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.Result
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedValue
import run.qontract.core.utilities.toMap
import run.qontract.core.value.EmptyString
import run.qontract.core.value.Value
import run.qontract.mock.Expectation
import run.qontract.mock.MockScenario
import run.qontract.mock.writeBadRequest
import java.io.Closeable

class ContractFake(contractInfo: List<Pair<ContractBehaviour, List<MockScenario>>> = emptyList(), host: String = "localhost", port: Int = 9000) : Closeable {
    constructor(gherkinData: String, stubInfo: List<MockScenario> = emptyList(), host: String = "localhost", port: Int = 9000) : this(listOf(Pair(ContractBehaviour(gherkinData), stubInfo)), host, port)

    val endPoint = "http://$host:$port"

    private val contractBehaviours = contractInfo.map { it.first }
    private val expectations = contractBehaviours.zip(contractInfo.map { it.second }).flatMap { (behaviour, mocks) ->
        mocks.map { mock ->
            val (resolver, httpResponse) = behaviour.matchingMockResponse(mock)
            Triple(mock.request.toPattern(), resolver, httpResponse)
        }
    }

    private val server: ApplicationEngine = embeddedServer(Netty, host = host, port = port) {
        install(CORS) {
            method(HttpMethod.Options)
            method(HttpMethod.Get)
            method(HttpMethod.Post)
            method(HttpMethod.Put)
            method(HttpMethod.Delete)
            method(HttpMethod.Patch)
            header(HttpHeaders.Authorization)
            allowCredentials = true
            anyHost()
        }

        intercept(ApplicationCallPipeline.Call) {
            try {
                val httpRequest = ktorHttpRequestToHttpRequest(call)

                if (isSetupRequest(httpRequest)) {
                    setupServerState(httpRequest)
                    call.response.status(HttpStatusCode.OK)
                } else try {
                    when (val mock = expectations.find { (requestPattern, resolver, _) ->
                        requestPattern.matches(httpRequest, resolver) is Result.Success
                    }) {
                        null -> {
                            val responses = contractBehaviours.asSequence().map {
                                it.lookup(httpRequest)
                            }

                            val response = responses.firstOrNull {
                                it.headers.getOrDefault("X-Qontract-Result", "none") != "failure"
                            } ?: HttpResponse.ERROR_400

                            respondToKtorHttpResponse(call, response)
                        }
                        else -> {
                            val (_, _, response) = mock
                            respondToKtorHttpResponse(call, response)
                        }
                    }
                } finally {
                    contractBehaviours.forEach {
                        it.clearServerState()
                    }
                }
            }
            catch(e: ContractException) {
                writeBadRequest(call, e.report())
            }
            catch(e: Exception) {
                writeBadRequest(call, e.message)
            }
        }
    }

    override fun close() {
        server.stop(0, 5000)
    }

    private fun setupServerState(httpRequest: HttpRequest) {
        val body = httpRequest.body
        contractBehaviours.forEach { behaviour ->
            behaviour.setServerState(body?.let { toMap(it) } ?: mutableMapOf())
        }
    }

    private fun isSetupRequest(httpRequest: HttpRequest): Boolean {
        return httpRequest.path == "/_server_state" && httpRequest.method == "POST"
    }

    init {
        server.start()
    }
}

internal suspend fun ktorHttpRequestToHttpRequest(call: ApplicationCall): HttpRequest {
    val(body, formFields) = bodyFromCall(call)

    val requestHeaders = call.request.headers.toMap().mapValues { it.value[0] }

    return HttpRequest(method = call.request.httpMethod.value,
            path = call.request.path(),
            headers = requestHeaders,
            body = body,
            queryParams = toParams(call.request.queryParameters),
            formFields = formFields)
}

private suspend fun bodyFromCall(call: ApplicationCall): Pair<Value, Map<String, String>> {
    return if (call.request.contentType().match(ContentType.Application.FormUrlEncoded))
        Pair(EmptyString, call.receiveParameters().toMap().mapValues { (_, values) -> values.first() })
    else
        Pair(parsedValue(call.receiveText()), emptyMap())
}

internal fun toParams(queryParameters: Parameters) = queryParameters.toMap().mapValues { it.value.first() }

internal fun respondToKtorHttpResponse(call: ApplicationCall, httpResponse: HttpResponse) {
    val textContent = TextContent(httpResponse.body as String, ContentType.Application.Json, HttpStatusCode.fromValue(httpResponse.status))

    try {
        val headersControlledByEngine = listOf("content-type", "content-length")
        for ((name, value) in httpResponse.headers.filterNot { it.key.toLowerCase() in headersControlledByEngine }) {
            call.response.headers.append(name, value)
        }

        runBlocking { call.respond(textContent) }
    }
    catch(e: Exception)
    {
        println(e.toString())
    }
}
