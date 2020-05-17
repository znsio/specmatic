package run.qontract.fake

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.TextContent
import io.ktor.http.content.readAllParts
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.asStream
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedValue
import run.qontract.core.utilities.toMap
import run.qontract.core.utilities.valueMapToPlainJsonString
import run.qontract.core.value.EmptyString
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import run.qontract.mock.MockScenario
import run.qontract.nullLog
import java.io.ByteArrayOutputStream
import java.util.*

class ContractFake(private val contractInfo: List<Pair<ContractBehaviour, List<MockScenario>>> = emptyList(), host: String = "127.0.0.1", port: Int = 9000, private val log: (event: String) -> Unit = nullLog) : ContractStub {
    constructor(gherkinData: String, stubInfo: List<MockScenario> = emptyList(), host: String = "localhost", port: Int = 9000) : this(listOf(Pair(ContractBehaviour(gherkinData), stubInfo)), host, port)

    val endPoint = "http://$host:$port"

    private val stubs: Stubs = contractInfoToExpectations(contractInfo)

    private var qontractKafka: QontractKafka? = null

    override fun getKafkaInstance() = qontractKafka

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

                when {
                    isSetupRequest(httpRequest) -> {
                        setupServerState(httpRequest)
                        call.response.status(HttpStatusCode.OK)
                    }
                    else -> {
                        log(">> Request Start At ${Date()}")
                        log(httpRequest.toLogString("-> "))
                        val response = stubResponse(httpRequest, contractInfo, stubs)
                        log(response.toLogString("<- "))
                        log("<< Response At ${Date()} == ")
                        log(System.lineSeparator())
                        respondToKtorHttpResponse(call, response)
                    }
                }
            }
            catch(e: ContractException) {
                val response = badRequest(e.report())
                log(response.toLogString("<- "))
                log("<< Response At ${Date()} == ")
                respondToKtorHttpResponse(call, response)
            }
            catch(e: Throwable) {
                val response = badRequest(e.message)
                log(response.toLogString("<- "))
                log("<< Response At ${Date()} == ")
                respondToKtorHttpResponse(call, response)
            }
        }
    }

    override fun close() {
        server.stop(0, 5000)
        qontractKafka?.close()
    }

    private fun setupServerState(httpRequest: HttpRequest) {
        val body = httpRequest.body
        val serverState = body?.let { toMap(it) } ?: mutableMapOf()

        log("# >> Request Sent At ${Date()}")
        log(startLinesWith(valueMapToPlainJsonString(serverState), "# "))

        contractInfo.forEach { (behaviour, _) ->
            behaviour.setServerState(serverState)
        }

        log("# << Complete At ${Date()}")
    }

    private fun isSetupRequest(httpRequest: HttpRequest): Boolean {
        return httpRequest.path == "/_server_state" && httpRequest.method == "POST"
    }

    init {
        server.start()

        if(stubs.kafka.isNotEmpty()) {
            val qontractKafka = QontractKafka()
            this.qontractKafka = qontractKafka

            for(stub in stubs.kafka) {
                val (target, key, content) = stub.kafkaMessage

                if(key != null)
                    qontractKafka.send(target, key.string, content.toStringValue())
                else
                    qontractKafka.send(target, content.toStringValue())
            }
        }
    }
}

internal suspend fun ktorHttpRequestToHttpRequest(call: ApplicationCall): HttpRequest {
    val(body, formFields, multiPartFormData) = bodyFromCall(call)

    val requestHeaders = call.request.headers.toMap().mapValues { it.value[0] }

    return HttpRequest(method = call.request.httpMethod.value,
            path = call.request.path(),
            headers = requestHeaders,
            body = body,
            queryParams = toParams(call.request.queryParameters),
            formFields = formFields,
            multiPartFormData = multiPartFormData)
}

@OptIn(KtorExperimentalAPI::class)
private suspend fun bodyFromCall(call: ApplicationCall): Triple<Value, Map<String, String>, List<MultiPartFormDataValue>> {
    return when {
        call.request.contentType().match(ContentType.Application.FormUrlEncoded) -> Triple(EmptyString, call.receiveParameters().toMap().mapValues { (_, values) -> values.first() }, emptyList())
        call.request.isMultipart() -> {
            val multiPartData = call.receiveMultipart()
            val boundary = call.request.contentType().parameter("boundary") ?: "boundary"

            val parts = multiPartData.readAllParts().map {
                when (it) {
                    is PartData.FileItem -> {
                        val content: String = it.provider().asStream().use { inputStream ->
                             inputStream.bufferedReader().readText()
                        }
                        MultiPartFileValue(it.name ?: "", it.originalFileName ?: "", "${it.contentType?.contentType}/${it.contentType?.contentSubtype}", null, content, boundary)
                    }
                    is PartData.FormItem -> {
                        MultiPartContentValue(it.name ?: "", StringValue(it.value), boundary)
                    }
                    is PartData.BinaryItem -> {
                        val content = it.provider().asStream().use { input ->
                            val output = ByteArrayOutputStream()
                            input.copyTo(output)
                            output.toString()
                        }

                        MultiPartContentValue(it.name ?: "", StringValue(content), boundary)
                    }
                }
            }

            Triple(EmptyString, emptyMap(), parts)
        }
        else -> Triple(parsedValue(call.receiveText()), emptyMap(), emptyList())
    }
}

internal fun toParams(queryParameters: Parameters) = queryParameters.toMap().mapValues { it.value.first() }

internal fun respondToKtorHttpResponse(call: ApplicationCall, httpResponse: HttpResponse) {
    val headerString = httpResponse.headers.get("Content-Type") ?: "text/plain"
    val textContent = TextContent(httpResponse.body?.toStringValue() ?: "", ContentType.parse(headerString), HttpStatusCode.fromValue(httpResponse.status))

    try {
        val headersControlledByEngine = HttpHeaders.UnsafeHeadersList.map { it.toLowerCase() }
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

fun stubResponse(httpRequest: HttpRequest, contractInfo: List<Pair<ContractBehaviour, List<MockScenario>>>, stubs: Stubs): HttpResponse {
    return try {
        when (val mock = stubs.http.find { (requestPattern, _, resolver) ->
            requestPattern.matches(httpRequest, resolver.copy(findMissingKey = checkAllKeys)) is Result.Success
        }) {
            null -> {
                val responses = contractInfo.asSequence().map { (behaviour, _) ->
                    behaviour.lookupResponse(httpRequest)
                }

                responses.firstOrNull {
                    it.headers.getOrDefault("X-Qontract-Result", "none") != "failure"
                } ?: HttpResponse(400, responses.map {
                    it.body ?: EmptyString
                }.filter { it != EmptyString }.joinToString("\n\n"))
            }
            else -> mock.third
        }
    } finally {
        contractInfo.forEach { (behaviour, _) ->
            behaviour.clearServerState()
        }
    }
}

fun contractInfoToExpectations(contractInfo: List<Pair<ContractBehaviour, List<MockScenario>>>): Stubs {
    return contractInfo.fold(Stubs()) { stubsAcc, (behaviour, mocks) ->
        val newStubs = mocks.fold(Stubs()) { stubs, mock ->
            if(mock.kafkaMessage != null) {
                Stubs(stubs.http, stubs.kafka.plus(KafkaStub(mock.kafkaMessage)))
            } else {
                val (resolver, scenario, httpResponse) = behaviour.matchingMockResponse(mock)

                val requestPattern = mock.request.toPattern()
                val requestPatternWithHeaderAncestor = requestPattern.copy(headersPattern = requestPattern.headersPattern.copy(ancestorHeaders = scenario.httpRequestPattern.headersPattern.pattern))

                Stubs(stubs.http.plus(HttpStub(requestPatternWithHeaderAncestor, httpResponse, resolver)), stubs.kafka)
            }
        }

        Stubs(stubsAcc.http.plus(newStubs.http), stubsAcc.kafka.plus(newStubs.kafka))
    }
}

fun badRequest(errorMessage: String?): HttpResponse {
    return HttpResponse(HttpStatusCode.UnprocessableEntity.value, errorMessage, mapOf("X-Qontract-Result" to "failure"))
}

interface Stub
data class HttpStub(val requestPattern: HttpRequestPattern, val response: HttpResponse, val resolver: Resolver) : Stub {
    val first = requestPattern
    val second = resolver
    val third = response
}

data class KafkaStub(val kafkaMessage: KafkaMessage) : Stub

data class Stubs(val http: List<HttpStub> = emptyList(), val kafka: List<KafkaStub> = emptyList())
