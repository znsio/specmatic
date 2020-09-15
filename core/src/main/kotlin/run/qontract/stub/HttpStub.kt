package run.qontract.stub

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.asStream
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import run.qontract.LogTail
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedValue
import run.qontract.core.pattern.withoutOptionality
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.utilities.toMap
import run.qontract.core.utilities.valueMapToPlainJsonString
import run.qontract.core.value.*
import run.qontract.mock.NoMatchingScenario
import run.qontract.mock.ScenarioStub
import run.qontract.mock.mockFromJSON
import run.qontract.mock.validateMock
import run.qontract.nullLog
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.text.isEmpty
import kotlin.text.toCharArray
import kotlin.text.toLowerCase

class HttpStub(private val features: List<Feature>, _httpStubs: List<HttpStubData> = emptyList(), host: String = "127.0.0.1", port: Int = 9000, private val log: (event: String) -> Unit = nullLog, private val strictMode: Boolean = false, keyStoreData: KeyStoreData? = null) : ContractStub {
    constructor(feature: Feature, scenarioStubs: List<ScenarioStub> = emptyList(), host: String = "localhost", port: Int = 9000, log: (event: String) -> Unit = nullLog) : this(listOf(feature), contractInfoToHttpExpectations(listOf(Pair(feature, scenarioStubs))), host, port, log)
    constructor(gherkinData: String, scenarioStubs: List<ScenarioStub> = emptyList(), host: String = "localhost", port: Int = 9000, log: (event: String) -> Unit = nullLog) : this(Feature(gherkinData), scenarioStubs, host, port, log)

    private var httpStubs = Vector(_httpStubs)
    val endPoint = endPointFromHostAndPort(host, port, keyStoreData)

    private val environment = applicationEngineEnvironment {
        module {
            install(CORS) {
                method(HttpMethod.Options)
                method(HttpMethod.Get)
                method(HttpMethod.Post)
                method(HttpMethod.Put)
                method(HttpMethod.Delete)
                method(HttpMethod.Patch)
                header(HttpHeaders.Authorization)
                allowCredentials = true
                allowNonSimpleContentTypes = true

                features.flatMap { feature ->
                    feature.scenarios.flatMap { scenario ->
                        scenario.httpRequestPattern.headersPattern.pattern.keys.map { withoutOptionality(it) }
                    }
                }.forEach { header(it) }

                anyHost()
            }

            intercept(ApplicationCallPipeline.Call) {
                val logs = mutableListOf<String>()

                try {
                    val httpRequest = ktorHttpRequestToHttpRequest(call)
                    logs.add(httpRequestLog(httpRequest))

                    val (httpResponse, responseLog) = when {
                        isFetchLogRequest(httpRequest) -> handleFetchLogRequest()
                        isFetchLoadLogRequest(httpRequest) -> handleFetchLoadLogRequest()
                        isFetchContractsRequest(httpRequest) -> handleFetchContractsRequest()
                        isExpectationCreation(httpRequest) -> handleExpectationCreationRequest(httpRequest)
                        isStateSetupRequest(httpRequest) -> handleStateSetupRequest(httpRequest)
                        else -> serveStubResponse(httpRequest)
                    }

                    respondToKtorHttpResponse(call, httpResponse)
                    logs.add(responseLog ?: httpResponseLog(httpResponse))
                    log(logs.joinToString(System.lineSeparator()))
                }
                catch(e: ContractException) {
                    val response = badRequest(e.report())
                    logs.add(httpResponseLog(response))
                    respondToKtorHttpResponse(call, response)
                    log(logs.joinToString(System.lineSeparator()))
                }
                catch(e: Throwable) {
                    val response = badRequest(exceptionCauseMessage(e))
                    logs.add(httpResponseLog(response))
                    respondToKtorHttpResponse(call, response)
                    log(logs.joinToString(System.lineSeparator()))
                }
            }
        }

        when {
            keyStoreData == null -> connector {
                this.host = host
                this.port = port
            }
            else -> sslConnector(keyStore = keyStoreData.keyStore, keyAlias = keyStoreData.keyAlias, privateKeyPassword = { keyStoreData.keyPassword.toCharArray() }, keyStorePassword = { keyStoreData.keyPassword.toCharArray() }) {
                this.host = host
                this.port = port
            }
        }
    }

    private val server: ApplicationEngine = embeddedServer(Netty, environment)

    private fun handleFetchLoadLogRequest(): Pair<HttpResponse, Nothing?> =
            Pair(HttpResponse.OK(StringValue(LogTail.getSnapshot())), null)

    private fun handleFetchContractsRequest(): Pair<HttpResponse, Nothing?> =
            Pair(HttpResponse.OK(StringValue(features.joinToString("\n") { it.name })), null)

    private fun handleFetchLogRequest(): Pair<HttpResponse, Nothing?> =
            Pair(HttpResponse.OK(StringValue(LogTail.getString())), null)

    private fun serveStubResponse(httpRequest: HttpRequest): Pair<HttpResponse, Nothing?> =
            Pair(stubResponse(httpRequest, features, httpStubs, strictMode), null)

    private fun handleExpectationCreationRequest(httpRequest: HttpRequest): Pair<HttpResponse, Nothing?> {
        return try {
            if(httpRequest.body.toStringValue().isEmpty())
                throw ContractException("Expectation payload was empty")

            val mock = stringToMockScenario(httpRequest.body)
            createStub(mock)

            Pair(HttpResponse.OK, null)
        }
        catch(e: ContractException) {
            Pair(HttpResponse(status = 400, headers = mapOf(QONTRACT_RESULT_HEADER to "failure"), body = StringValue(e.report())), null)
        }
        catch (e: Exception) {
            Pair(HttpResponse(status = 400, headers = mapOf(QONTRACT_RESULT_HEADER to "failure"), body = StringValue(e.localizedMessage ?: e.message ?: e.javaClass.name)), null)
        }
    }

    // For use from Karate
    fun createStub(json: String) {
        val mock = stringToMockScenario(StringValue(json))
        createStub(mock)
    }

    fun createStub(stub: ScenarioStub) {
        if (stub.kafkaMessage != null) throw ContractException("Mocking Kafka messages over HTTP is not supported right now")

        val results = features.asSequence().map { feature ->
            try {
                val mockResponse = softCastResponseToXML(feature.matchingStub(stub.request, stub.response))
                Pair(Result.Success(), mockResponse)
            } catch (e: NoMatchingScenario) {
                Pair(Result.Failure(e.localizedMessage), null)
            }
        }

        val result = results.find { it.first is Result.Success }

        when (result?.first) {
            is Result.Success -> httpStubs.insertElementAt(result.second, 0)
            else -> throw NoMatchingScenario(Results(results.map { it.first }.toMutableList()).report())
        }
    }

    override fun close() {
        server.stop(0, 5000)
    }

    private fun handleStateSetupRequest(httpRequest: HttpRequest): Pair<HttpResponse, String> {
        val body = httpRequest.body
        val serverState = toMap(body)

        val stateRequestLog = "# >> Request Sent At ${Date()}\n${startLinesWith(valueMapToPlainJsonString(serverState), "# ")}"

        features.forEach { feature ->
            feature.setServerState(serverState)
        }

        val completeLog = "$stateRequestLog\n# << Complete At ${Date()}"

        return Pair(HttpResponse.OK, completeLog)
    }

    init {
        server.start()
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
    val headerString = httpResponse.headers["Content-Type"] ?: httpResponse.body.httpContentType
    val textContent = TextContent(httpResponse.body.toStringValue(), ContentType.parse(headerString), HttpStatusCode.fromValue(httpResponse.status))

    val headersControlledByEngine = HttpHeaders.UnsafeHeadersList.map { it.toLowerCase() }
    for ((name, value) in httpResponse.headers.filterNot { it.key.toLowerCase() in headersControlledByEngine }) {
        call.response.headers.append(name, value)
    }

    runBlocking { call.respond(textContent) }
}

fun stubResponse(httpRequest: HttpRequest, features: List<Feature>, stubs: List<HttpStubData>, strictMode: Boolean): HttpResponse {
    return try {
        val matchResults = stubs.map {
            val (requestPattern, _, resolver) = it
            Pair(requestPattern.matches(httpRequest, resolver.copy(findMissingKey = checkAllKeys)), it)
        }

        val mock = matchResults.find { (result, _) -> result is Result.Success }?.second

        mock?.softCastResponseToXML()?.response ?: when(strictMode) {
            true -> httpResponse(matchResults)
            else -> httpResponse(features, httpRequest)
        }
    } finally {
        features.forEach { feature ->
            feature.clearServerState()
        }
    }
}

private fun httpResponse(features: List<Feature>, httpRequest: HttpRequest): HttpResponse {
    val responses = features.asSequence().map {
        it.stubResponse(httpRequest)
    }.toList()

    return when (val successfulResponse = responses.firstOrNull { it.headers.getOrDefault(QONTRACT_RESULT_HEADER, "none") != "failure" }) {
        null -> {
            val body = when {
                responses.all { it.headers.getOrDefault("X-Qontract-Empty", "none") == "true" } -> StringValue(PATH_NOT_RECOGNIZED_ERROR)
                else -> StringValue(responses.map {
                    it.body
                }.filter { it != EmptyString }.joinToString("\n\n"))
            }

            HttpResponse(400, headers = mapOf(QONTRACT_RESULT_HEADER to "failure"), body = body)
        }
        else -> successfulResponse
    }
}

private fun httpResponse(matchResults: List<Pair<Result, HttpStubData>>): HttpResponse {
    val failureResults = matchResults.map { it.first }

    val results = Results(failureResults.toMutableList()).withoutFluff()
    return HttpResponse(400, headers = mapOf(QONTRACT_RESULT_HEADER to "failure"), body = StringValue("STRICT MODE ON\n\n${results.report()}"))
}

fun stubResponse(httpRequest: HttpRequest, contractInfo: List<Pair<Feature, List<ScenarioStub>>>, stubs: StubDataItems): HttpResponse {
    return try {
        when (val mock = stubs.http.find { (requestPattern, _, resolver) ->
            requestPattern.matches(httpRequest, resolver.copy(findMissingKey = checkAllKeys)) is Result.Success
        }) {
            null -> {
                val responses = contractInfo.asSequence().map { (feature, _) ->
                    feature.lookupResponse(httpRequest)
                }

                responses.firstOrNull {
                    it.headers.getOrDefault(QONTRACT_RESULT_HEADER, "none") != "failure"
                } ?: HttpResponse(400, responses.map {
                    it.body
                }.filter { it != EmptyString }.joinToString("\n\n"))
            }
            else -> mock.response
        }
    } finally {
        contractInfo.forEach { (feature, _) ->
            feature.clearServerState()
        }
    }
}

fun contractInfoToHttpExpectations(contractInfo: List<Pair<Feature, List<ScenarioStub>>>): List<HttpStubData> {
    return contractInfo.flatMap { (feature, mocks) ->
        mocks.filter { it.kafkaMessage == null }.map { mock ->
            feature.matchingStub(mock)
        }
    }
}

fun badRequest(errorMessage: String?): HttpResponse {
    return HttpResponse(HttpStatusCode.BadRequest.value, errorMessage, mapOf(QONTRACT_RESULT_HEADER to "failure"))
}

internal fun httpResponseLog(response: HttpResponse): String =
        "${response.toLogString("<- ")}\n<< Response At ${Date()} == "

internal fun httpRequestLog(httpRequest: HttpRequest): String =
        ">> Request Start At ${Date()}\n${httpRequest.toLogString("-> ")}"

fun endPointFromHostAndPort(host: String, port: Int?, keyStoreData: KeyStoreData?): String {
    val protocol = when(keyStoreData) {
        null -> "http"
        else -> "https"
    }

    val computedPortString = when(port) {
        80, null -> ""
        else -> ":$port"
    }

    return "$protocol://$host$computedPortString"
}

internal fun isFetchLogRequest(httpRequest: HttpRequest): Boolean =
        httpRequest.path == "/_qontract/log" && httpRequest.method == "GET"

internal fun isFetchContractsRequest(httpRequest: HttpRequest): Boolean =
        httpRequest.path == "/_qontract/contracts" && httpRequest.method == "GET"

internal fun isFetchLoadLogRequest(httpRequest: HttpRequest): Boolean =
        httpRequest.path == "/_qontract/load_log" && httpRequest.method == "GET"

internal fun isExpectationCreation(httpRequest: HttpRequest) =
        httpRequest.path == "/_qontract/expectations" && httpRequest.method == "POST"

internal fun isStateSetupRequest(httpRequest: HttpRequest): Boolean =
        httpRequest.path == "/_qontract/state" && httpRequest.method == "POST"

fun softCastResponseToXML(mockResponse: HttpStubData): HttpStubData =
        mockResponse.copy(response = mockResponse.response.copy(body = softCastValueToXML(mockResponse.response.body)))

fun softCastValueToXML(body: Value): Value {
    return when(body) {
        is StringValue -> try {
            XMLNode(body.string)
        } catch (e: Throwable) {
            body
        }
        else -> body
    }
}

fun stringToMockScenario(text: Value): ScenarioStub {
    val mockSpec =
            jsonStringToValueMap(text.toStringValue()).also {
                validateMock(it)
            }

    return mockFromJSON(mockSpec)
}
