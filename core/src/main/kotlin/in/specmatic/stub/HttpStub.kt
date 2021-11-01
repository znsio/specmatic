package `in`.specmatic.stub

import `in`.specmatic.core.*
import `in`.specmatic.core.log.HttpLogMessage
import `in`.specmatic.core.log.LogMessage
import `in`.specmatic.core.log.LogTail
import `in`.specmatic.core.log.dontPrintToConsole
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.utilities.jsonStringToValueMap
import `in`.specmatic.core.utilities.toMap
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.mock.NoMatchingScenario
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.mock.mockFromJSON
import `in`.specmatic.mock.validateMock
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.asStream
import io.ktor.util.toMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.text.toCharArray

data class HttpStubResponse(val response: HttpResponse, val delayInSeconds: Int? = null, val contractPath: String = "")

class HttpStub(private val features: List<Feature>, _httpStubs: List<HttpStubData> = emptyList(), host: String = "127.0.0.1", port: Int = 9000, private val log: (event: LogMessage) -> Unit = dontPrintToConsole, private val strictMode: Boolean = false, keyData: KeyData? = null, val passThroughTargetBase: String = "", val httpClientFactory: HttpClientFactory = HttpClientFactory(), val workingDirectory: WorkingDirectory? = null) : ContractStub {
    constructor(feature: Feature, scenarioStubs: List<ScenarioStub> = emptyList(), host: String = "localhost", port: Int = 9000, log: (event: LogMessage) -> Unit = dontPrintToConsole) : this(listOf(feature), contractInfoToHttpExpectations(listOf(Pair(feature, scenarioStubs))), host, port, log)
    constructor(gherkinData: String, scenarioStubs: List<ScenarioStub> = emptyList(), host: String = "localhost", port: Int = 9000, log: (event: LogMessage) -> Unit = dontPrintToConsole) : this(parseGherkinStringToFeature(gherkinData), scenarioStubs, host, port, log)

    private val threadSafeHttpStubs = ThreadSafeListOfStubs(_httpStubs.toMutableList())
    val endPoint = endPointFromHostAndPort(host, port, keyData)

    private val environment = applicationEngineEnvironment {
        module {
            install(CORS) {
                method(HttpMethod.Options)
                method(HttpMethod.Get)
                method(HttpMethod.Post)
                method(HttpMethod.Put)
                method(HttpMethod.Delete)
                method(HttpMethod.Patch)

                allowHeaders {
                    true
                }

                allowCredentials = true
                allowNonSimpleContentTypes = true

                anyHost()
            }

            intercept(ApplicationCallPipeline.Call) {
                val httpLogMessage = HttpLogMessage()

                try {
                    val httpRequest = ktorHttpRequestToHttpRequest(call)
                    httpLogMessage.addRequest(httpRequest)

                    val httpStubResponse: HttpStubResponse = when {
                        isFetchLogRequest(httpRequest) -> handleFetchLogRequest()
                        isFetchLoadLogRequest(httpRequest) -> handleFetchLoadLogRequest()
                        isFetchContractsRequest(httpRequest) -> handleFetchContractsRequest()
                        isExpectationCreation(httpRequest) -> handleExpectationCreationRequest(httpRequest)
                        isStateSetupRequest(httpRequest) -> handleStateSetupRequest(httpRequest)
                        else -> serveStubResponse(httpRequest)
                    }

                    respondToKtorHttpResponse(call, httpStubResponse.response, httpStubResponse.delayInSeconds)
                    httpLogMessage.addResponse(httpStubResponse)
                }
                catch(e: ContractException) {
                    val response = badRequest(e.report())
                    httpLogMessage.addResponse(response)
                    respondToKtorHttpResponse(call, response)
                }
                catch(e: Throwable) {
                    val response = badRequest(exceptionCauseMessage(e))
                    httpLogMessage.addResponse(response)
                    respondToKtorHttpResponse(call, response)
                }

                log(httpLogMessage)
            }
        }

        when (keyData) {
            null -> connector {
                this.host = host
                this.port = port
            }
            else -> sslConnector(keyStore = keyData.keyStore, keyAlias = keyData.keyAlias, privateKeyPassword = { keyData.keyPassword.toCharArray() }, keyStorePassword = { keyData.keyPassword.toCharArray() }) {
                this.host = host
                this.port = port
            }
        }
    }

    private val server: ApplicationEngine = embeddedServer(Netty, environment, configure = {
        this.callGroupSize = 20
    })

    private fun handleFetchLoadLogRequest(): HttpStubResponse =
            HttpStubResponse(HttpResponse.OK(StringValue(LogTail.getSnapshot())))

    private fun handleFetchContractsRequest(): HttpStubResponse =
            HttpStubResponse(HttpResponse.OK(StringValue(features.joinToString("\n") { it.name })))

    private fun handleFetchLogRequest(): HttpStubResponse =
            HttpStubResponse(HttpResponse.OK(StringValue(LogTail.getString())))

    private fun serveStubResponse(httpRequest: HttpRequest): HttpStubResponse =
            getHttpResponse(httpRequest, features, threadSafeHttpStubs, strictMode, passThroughTargetBase, httpClientFactory)

    private fun handleExpectationCreationRequest(httpRequest: HttpRequest): HttpStubResponse {
        return try {
            if(httpRequest.body.toStringLiteral().isEmpty())
                throw ContractException("Expectation payload was empty")

            val mock = stringToMockScenario(httpRequest.body)
            val stub: HttpStubData? = createStub(mock)

            HttpStubResponse(HttpResponse.OK, contractPath = stub?.contractPath ?: "")
        }
        catch(e: ContractException) {
            HttpStubResponse(HttpResponse(status = 400, headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"), body = StringValue(e.report())))
        }
        catch (e: Exception) {
            HttpStubResponse(HttpResponse(status = 400, headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"), body = StringValue(e.localizedMessage ?: e.message ?: e.javaClass.name)))
        }
    }

    // For use from Karate
    fun createStub(json: String) {
        val mock = stringToMockScenario(StringValue(json))
        createStub(mock)
    }

    fun createStub(stub: ScenarioStub): HttpStubData? {
        if (stub.kafkaMessage != null) throw ContractException("Mocking Kafka messages over HTTP is not supported right now")

        val results = features.asSequence().map { feature ->
            try {
                val stubData: HttpStubData = softCastResponseToXML(feature.matchingStub(stub.request, stub.response))
                Pair(Result.Success(), stubData)
            } catch (e: NoMatchingScenario) {
                Pair(Result.Failure(e.localizedMessage), null)
            }
        }

        val result: Pair<Result, HttpStubData?>? = results.find { it.first is Result.Success }

        when (result?.first) {
            is Result.Success -> threadSafeHttpStubs.addToStub(result, stub)
            else -> throw NoMatchingScenario(Results(results.map { it.first }.toMutableList()).report(stub.request))
        }

        return result.second
    }

    override fun close() {
        server.stop(0, 5000)
        workingDirectory?.delete()
    }

    private fun handleStateSetupRequest(httpRequest: HttpRequest): HttpStubResponse {
        val body = httpRequest.body
        val serverState = toMap(body)

        features.forEach { feature ->
            feature.setServerState(serverState)
        }

        return HttpStubResponse(HttpResponse.OK)
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

private suspend fun bodyFromCall(call: ApplicationCall): Triple<Value, Map<String, String>, List<MultiPartFormDataValue>> {
    return when {
        call.request.httpMethod == HttpMethod.Get -> Triple(EmptyString, emptyMap(), emptyList())
        call.request.contentType().match(ContentType.Application.FormUrlEncoded) -> Triple(EmptyString, call.receiveParameters().toMap().mapValues { (_, values) -> values.first() }, emptyList())
        call.request.isMultipart() -> {
            val multiPartData = call.receiveMultipart()
            val boundary = call.request.contentType().parameter("boundary") ?: "boundary"

            val parts = multiPartData.readAllParts().map {
                when (it) {
                    is PartData.FileItem -> {
                        val content = it.provider().asStream().use { inputStream ->
                             MultiPartContent(inputStream.readBytes())
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

internal fun respondToKtorHttpResponse(call: ApplicationCall, httpResponse: HttpResponse, delayInSeconds: Int? = null) {
    val contentType = httpResponse.headers["Content-Type"] ?: httpResponse.body.httpContentType
    val textContent = TextContent(httpResponse.body.toStringLiteral(), ContentType.parse(contentType), HttpStatusCode.fromValue(httpResponse.status))

    val headersControlledByEngine = HttpHeaders.UnsafeHeadersList.map { it.lowercase() }
    for ((name, value) in httpResponse.headers.filterNot { it.key.lowercase() in headersControlledByEngine }) {
        call.response.headers.append(name, value)
    }

    runBlocking {
        if(delayInSeconds != null) {
            delay(delayInSeconds * 1000L)
        }

        call.respond(textContent)
    }
}

fun getHttpResponse(httpRequest: HttpRequest, features: List<Feature>, threadSafeStubs: ThreadSafeListOfStubs, strictMode: Boolean, passThroughTargetBase: String = "", httpClientFactory: HttpClientFactory? = null): HttpStubResponse {
    return try {
        val (matchResults, stubResponse) = stubbedResponse(threadSafeStubs, httpRequest)

        stubResponse
            ?: if(httpClientFactory != null && passThroughTargetBase.isNotBlank()) {
                passThroughResponse(httpRequest, passThroughTargetBase, httpClientFactory)
            } else {
                if (strictMode)
                    HttpStubResponse(http400Response(httpRequest, matchResults))
                else
                    fakeHttpResponse(features, httpRequest)
            }
    } finally {
        features.forEach { feature ->
            feature.clearServerState()
        }
    }
}

const val QONTRACT_SOURCE_HEADER = "X-$APPLICATION_NAME-Source"

fun passThroughResponse(httpRequest: HttpRequest, passThroughUrl: String, httpClientFactory: HttpClientFactory): HttpStubResponse {
    val response = httpClientFactory.client(passThroughUrl).execute(httpRequest)
    return HttpStubResponse(response.copy(headers = response.headers.plus(QONTRACT_SOURCE_HEADER to "proxy")))
}

private fun stubbedResponse(
    threadSafeStubs: ThreadSafeListOfStubs,
    httpRequest: HttpRequest
): Pair<List<Pair<Result, HttpStubData>>, HttpStubResponse?> {
    val matchResults = threadSafeStubs.matchResults { stubs ->
        stubs.map {
            val (requestPattern, _, resolver) = it
            Pair(requestPattern.matches(httpRequest, resolver.copy(findKeyError = checkAllKeys)), it)
        }
    }

    val mock = matchResults.find { (result, _) -> result is Result.Success }?.second

    val stubResponse = mock?.let {
        val softCastResponse = it.softCastResponseToXML(httpRequest).response
        HttpStubResponse(softCastResponse, it.delayInSeconds, it.contractPath)
    }

    return Pair(matchResults, stubResponse)
}

private fun fakeHttpResponse(features: List<Feature>, httpRequest: HttpRequest): HttpStubResponse {
    val responses = features.asSequence().map {
        Pair(it, it.stubResponse(httpRequest))
    }.toList()

    return when (val fakeResponse = responses.firstOrNull { it.second.headers.getOrDefault(SPECMATIC_RESULT_HEADER, "none") != "failure" }) {
        null -> {
            val (headers, body) = when {
                responses.all { it.second.headers.getOrDefault(SPECMATIC_EMPTY_HEADER, "none") == "true" } -> {
                    Pair(mapOf(SPECMATIC_EMPTY_HEADER to "true"), StringValue(pathNotRecognizedMessage(httpRequest)))
                }
                else -> Pair(emptyMap(), StringValue(responses.map {
                    it.second.body
                }.filter { it != EmptyString }.joinToString("\n\n")))
            }

            HttpStubResponse(HttpResponse(400, headers = headers.plus(mapOf(SPECMATIC_RESULT_HEADER to "failure")), body = body))
        }
        else -> HttpStubResponse(fakeResponse.second.withRandomResultHeader(), contractPath = fakeResponse.first.path)
    }
}

private fun http400Response(httpRequest: HttpRequest, matchResults: List<Pair<Result, HttpStubData>>): HttpResponse {
    val failureResults = matchResults.map { it.first }

    val results = Results(failureResults.toMutableList()).withoutFluff()
    return HttpResponse(400, headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"), body = StringValue("STRICT MODE ON\n\n${results.report(httpRequest)}"))
}

fun stubResponse(httpRequest: HttpRequest, contractInfo: List<Pair<Feature, List<ScenarioStub>>>, stubs: StubDataItems): HttpResponse {
    return try {
        when (val mock = stubs.http.find { (requestPattern, _, resolver) ->
            requestPattern.matches(httpRequest, resolver.copy(findKeyError = checkAllKeys)) is Result.Success
        }) {
            null -> {
                val responses = contractInfo.asSequence().map { (feature, _) ->
                    feature.lookupResponse(httpRequest)
                }

                responses.firstOrNull {
                    it.headers.getOrDefault(SPECMATIC_RESULT_HEADER, "none") != "failure"
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
    return HttpResponse(HttpStatusCode.BadRequest.value, errorMessage, mapOf(SPECMATIC_RESULT_HEADER to "failure"))
}

internal fun httpResponseLog(response: HttpResponse): String =
        "${response.toLogString("<- ")}\n<< Response At ${Date()} == "

internal fun httpRequestLog(httpRequest: HttpRequest): String =
        ">> Request Start At ${Date()}\n${httpRequest.toLogString("-> ")}"

fun endPointFromHostAndPort(host: String, port: Int?, keyData: KeyData?): String {
    val protocol = when(keyData) {
        null -> "http"
        else -> "https"
    }

    val computedPortString = when(port) {
        80, null -> ""
        else -> ":$port"
    }

    return "$protocol://$host$computedPortString"
}

internal fun isPath(path: String?, lastPart: String): Boolean {
    return path == "/_$APPLICATION_NAME_LOWER_CASE_LEGACY/$lastPart" || path == "/_$APPLICATION_NAME_LOWER_CASE/$lastPart"
}

internal fun isFetchLogRequest(httpRequest: HttpRequest): Boolean =
    isPath(httpRequest.path, "log") && httpRequest.method == "GET"

internal fun isFetchContractsRequest(httpRequest: HttpRequest): Boolean =
    isPath(httpRequest.path, "contracts") && httpRequest.method == "GET"

internal fun isFetchLoadLogRequest(httpRequest: HttpRequest): Boolean =
    isPath(httpRequest.path, "load_log") && httpRequest.method == "GET"

internal fun isExpectationCreation(httpRequest: HttpRequest) =
    isPath(httpRequest.path, "expectations") && httpRequest.method == "POST"

internal fun isStateSetupRequest(httpRequest: HttpRequest): Boolean =
    isPath(httpRequest.path, "state") && httpRequest.method == "POST"

fun softCastResponseToXML(mockResponse: HttpStubData): HttpStubData =
        mockResponse.copy(response = mockResponse.response.copy(body = softCastValueToXML(mockResponse.response.body)))

fun softCastValueToXML(body: Value): Value {
    return when(body) {
        is StringValue -> try {
            toXMLNode(body.string)
        } catch (e: Throwable) {
            body
        }
        else -> body
    }
}

fun stringToMockScenario(text: Value): ScenarioStub {
    val mockSpec =
            jsonStringToValueMap(text.toStringLiteral()).also {
                validateMock(it)
            }

    return mockFromJSON(mockSpec)
}
