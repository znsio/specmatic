package `in`.specmatic.stub

import com.fasterxml.jackson.databind.ObjectMapper
import `in`.specmatic.core.*
import `in`.specmatic.core.log.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.mock.*
import `in`.specmatic.stub.report.StubApi
import `in`.specmatic.stub.report.StubUsageReport
import `in`.specmatic.test.HttpClient
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.Writer
import java.nio.charset.Charset
import java.util.*
import kotlin.text.toCharArray

data class HttpStubResponse(val response: HttpResponse, val delayInSeconds: Int? = null, val contractPath: String = "", val feature:Feature? = null, val scenario:Scenario? = null)

class HttpStub(
    private val features: List<Feature>,
    _httpStubs: List<HttpStubData> = emptyList(),
    host: String = "127.0.0.1",
    port: Int = 9000,
    private val log: (event: LogMessage) -> Unit = dontPrintToConsole,
    private val strictMode: Boolean = false,
    keyData: KeyData? = null,
    val passThroughTargetBase: String = "",
    val httpClientFactory: HttpClientFactory = HttpClientFactory(),
    val workingDirectory: WorkingDirectory? = null,
    val specmaticConfigPath:String? = null
) : ContractStub {
    constructor(
        feature: Feature,
        scenarioStubs: List<ScenarioStub> = emptyList(),
        host: String = "localhost",
        port: Int = 9000,
        log: (event: LogMessage) -> Unit = dontPrintToConsole
    ) : this(listOf(feature), contractInfoToHttpExpectations(listOf(Pair(feature, scenarioStubs))), host, port, log)

    constructor(
        gherkinData: String,
        scenarioStubs: List<ScenarioStub> = emptyList(),
        host: String = "localhost",
        port: Int = 9000,
        log: (event: LogMessage) -> Unit = dontPrintToConsole
    ) : this(parseGherkinStringToFeature(gherkinData), scenarioStubs, host, port, log)

    companion object {
        const val JSON_REPORT_PATH = "./build/reports/specmatic"
        const val JSON_REPORT_FILE_NAME = "stub_usage_report.json"
    }

    private val threadSafeHttpStubs = ThreadSafeListOfStubs(_httpStubs.filter { it.stubToken == null }.toMutableList())
    private val threadSafeHttpStubQueue = ThreadSafeListOfStubs(_httpStubs.filter { it.stubToken != null }.reversed().toMutableList())

    val logs:MutableList<StubApi> = mutableListOf()
    val allSpecApis:MutableList<StubApi> = mutableListOf()

    val stubCount: Int
        get() {
            return threadSafeHttpStubs.size
        }

    val transientStubCount: Int
        get() {
            return threadSafeHttpStubQueue.size
        }

    val endPoint = endPointFromHostAndPort(host, port, keyData)

    override val client = HttpClient(this.endPoint)

    private val sseBuffer: SSEBuffer = SSEBuffer()

    private val broadcastChannels: Vector<BroadcastChannel<SseEvent>> = Vector(50, 10)

    private val environment = applicationEngineEnvironment {
        module {
            install(DoubleReceive)

            install(CORS) {
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Patch)

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
                        isSseExpectationCreation(httpRequest) -> handleSseExpectationCreationRequest(httpRequest)
                        isStateSetupRequest(httpRequest) -> handleStateSetupRequest(httpRequest)
                        isFlushTransientStubsRequest(httpRequest) -> handleFlushTransientStubsRequest(httpRequest)
                        else -> serveStubResponse(httpRequest)
                    }

                    if (httpRequest.path!!.startsWith("""/features/default""")) {
                        logger.log("Incoming subscription on URL path ${httpRequest.path} ")
                        val channel: Channel<SseEvent> = Channel(10, BufferOverflow.DROP_OLDEST)
                        val broadcastChannel: BroadcastChannel<SseEvent> = channel.broadcast()
                        broadcastChannels.add(broadcastChannel)

                        val events: ReceiveChannel<SseEvent> = broadcastChannel.openSubscription()

                        try {
                            call.respondSse(events, sseBuffer, httpRequest)

                            broadcastChannels.remove(broadcastChannel)

                            close(events, channel, "Events handle was already closed after handling all events", "Channel was already handled after handling all events")
                        } catch(e: Throwable) {
                            logger.log(e, "Exception in the SSE module")

                            broadcastChannels.remove(broadcastChannel)

                            close(events, channel, "Events handle threw an exception on closing", "Channel through an exception on closing")
                        }
                    } else {
                        respondToKtorHttpResponse(call, httpStubResponse.response, httpStubResponse.delayInSeconds)
                        httpLogMessage.addResponse(httpStubResponse)
                    }
                } catch (e: ContractException) {
                    val response = badRequest(e.report())
                    httpLogMessage.addResponse(response)
                    respondToKtorHttpResponse(call, response)
                } catch (e: CouldNotParseRequest) {
                    httpLogMessage.addRequest(defensivelyExtractedRequestForLogging(call))

                    val response = badRequest("Could not parse request")
                    httpLogMessage.addResponse(response)

                    respondToKtorHttpResponse(call, response)
                } catch (e: Throwable) {
                    val response = internalServerError(exceptionCauseMessage(e) + "\n\n" + e.stackTraceToString())
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
            else -> sslConnector(
                keyStore = keyData.keyStore,
                keyAlias = keyData.keyAlias,
                privateKeyPassword = { keyData.keyPassword.toCharArray() },
                keyStorePassword = { keyData.keyPassword.toCharArray() }) {
                this.host = host
                this.port = port
            }
        }
    }

    private fun handleFlushTransientStubsRequest(httpRequest: HttpRequest): HttpStubResponse {
        val token = httpRequest.path?.removePrefix("/_specmatic/$TRANSIENT_MOCK/")

        threadSafeHttpStubQueue.removeWithToken(token)

        return HttpStubResponse(HttpResponse.OK)
    }

    private fun isFlushTransientStubsRequest(httpRequest: HttpRequest): Boolean {
        return httpRequest.method?.toLowerCasePreservingASCIIRules() == "delete" && httpRequest.path?.startsWith("/_specmatic/$TRANSIENT_MOCK") == true
    }

    private fun close(
        events: ReceiveChannel<SseEvent>,
        channel: Channel<SseEvent>,
        eventsError: String,
        channelError: String
    ) {
        try {
            events.cancel()
        } catch (e: Throwable) {
            logger.log("$eventsError (${exceptionCauseMessage(e)})")
        }

        try {
            channel.cancel()
        } catch (e: Throwable) {
            logger.log("$channelError (${exceptionCauseMessage(e)}")
        }
    }

    private suspend fun defensivelyExtractedRequestForLogging(call: ApplicationCall): HttpRequest {
        val request = HttpRequest().let {
            try {
                it.copy(method = call.request.httpMethod.toString())
            } catch (e: Throwable) {
                it
            }
        }.let {
            try {
                it.copy(path = call.request.path())
            } catch (e: Throwable) {
                it
            }
        }.let {
            val requestHeaders = call.request.headers.toMap().mapValues { it.value[0] }
            it.copy(headers = requestHeaders)
        }.let {
            val queryParams = toParams(call.request.queryParameters)
            it.copy(queryParams = queryParams)
        }.let {
            val bodyOrError = try {
                receiveText(call)
            } catch (e: Throwable) {
                "Could not get body. Got exception: ${exceptionCauseMessage(e)}\n\n${e.stackTraceToString()}"
            }

            it.copy(body = StringValue(bodyOrError))
        }
        return request
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
        getHttpResponse(
            httpRequest,
            features,
            threadSafeHttpStubs,
            threadSafeHttpStubQueue,
            strictMode,
            passThroughTargetBase,
            httpClientFactory
        ).also {
            if(it.response.specmaticResultHeaderValue() == "success") logRequestAndStubResponse(httpRequest, it)
        }

    private fun logRequestAndStubResponse(request: HttpRequest, response: HttpStubResponse) {
        logs.add(
            StubApi(
                response.scenario?.path,
                request.method,
                response.response.status,
                response.feature?.sourceProvider,
                response.feature?.sourceRepository,
                response.feature?.sourceRepositoryBranch,
                response.feature?.specification,
                response.feature?.serviceType,
            )
        )
    }

    private suspend fun handleExpectationCreationRequest(httpRequest: HttpRequest): HttpStubResponse {
        return try {
            if (httpRequest.body.toStringLiteral().isEmpty())
                throw ContractException("Expectation payload was empty")

            val mock: ScenarioStub = stringToMockScenario(httpRequest.body)
            val stub: HttpStubData = setExpectation(mock)

            HttpStubResponse(HttpResponse.OK, contractPath = stub?.contractPath ?: "")
        } catch (e: ContractException) {
            HttpStubResponse(
                HttpResponse(
                    status = 400,
                    headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"),
                    body = StringValue(e.report())
                )
            )
        } catch (e: NoMatchingScenario) {
            HttpStubResponse(
                HttpResponse(
                    status = 400,
                    headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"),
                    body = StringValue(e.report(httpRequest))
                )
            )
        } catch (e: Throwable) {
            HttpStubResponse(
                HttpResponse(
                    status = 400,
                    headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"),
                    body = StringValue(e.localizedMessage ?: e.message ?: e.javaClass.name)
                )
            )
        }
    }

    private suspend fun handleSseExpectationCreationRequest(httpRequest: HttpRequest): HttpStubResponse {
        return try {
            val sseEvent: SseEvent? = ObjectMapper().readValue(httpRequest.bodyString, SseEvent::class.java)

            if(sseEvent == null) {
                logger.debug("No Sse Event was found in the request:\n${httpRequest.toLogString("  ")}")
            } else if(sseEvent.bufferIndex == null) {
                logger.debug("Broadcasting event: $sseEvent")

                for (channel in broadcastChannels) {
                    channel.send(sseEvent)
                }
            } else {
                logger.debug("Adding event to buffer: $sseEvent")
                sseBuffer.add(sseEvent)
            }

            HttpStubResponse(HttpResponse.OK, contractPath = "")
        } catch(e: ContractException) {
            HttpStubResponse(
                HttpResponse(
                    status = 400,
                    headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"),
                    body = exceptionCauseMessage(e)
                )
            )
        }
        catch (e: Throwable) {
            HttpStubResponse(
                HttpResponse(
                    status = 500,
                    headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"),
                    body = exceptionCauseMessage(e) + "\n\n" + e.stackTraceToString()
                )
            )
        }
    }

    // Java helper
    override fun setExpectation(json: String) {
        val mock = stringToMockScenario(StringValue(json))
        setExpectation(mock)
    }

    fun setExpectation(stub: ScenarioStub): HttpStubData {
        val results = features.asSequence().map { feature ->
            try {
                val stubData: HttpStubData = softCastResponseToXML(
                    feature.matchingStub(
                        stub.request,
                        stub.response,
                        ContractAndStubMismatchMessages
                    )
                )
                Pair(Pair(Result.Success(), stubData), null)
            } catch (e: NoMatchingScenario) {
                Pair(null, e)
            }
        }

        val result: Pair<Pair<Result.Success, HttpStubData>?, NoMatchingScenario?>? = results.find { it.first != null }
        val firstResult: Pair<Result.Success, HttpStubData>? = result?.first

        when (firstResult) {
            null -> {
                val failures = results.map {
                    it.second?.results?.withoutFluff()?.results ?: emptyList()
                }.flatten().toList()

                val failureResults = Results(failures).withoutFluff()
                throw NoMatchingScenario(failureResults, cachedMessage = failureResults.report(stub.request))
            }
            else -> {
                val requestBodyRegex = parseRegex(stub.requestBodyRegex)
                val stubData = firstResult.second.copy(requestBodyRegex = requestBodyRegex)
                val resultWithRequestBodyRegex = Pair(firstResult.first, stubData)

                if(stub.stubToken != null) {
                    threadSafeHttpStubQueue.addToStub(resultWithRequestBodyRegex, stub)
                } else {
                    threadSafeHttpStubs.addToStub(resultWithRequestBodyRegex, stub)
                }
            }
        }

        return firstResult.second
    }

    private fun parseRegex(regex: String?): Regex? {
        return regex?.let {
            try {
                Regex(it)
            } catch(e: Throwable) {
                throw ContractException("Couldn't parse regex $regex", exceptionCause = e)
            }
        }
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

        features.forEach {
            it.scenarios.forEach { scenario ->
                if (scenario.isA2xxScenario()) {
                    allSpecApis.add(
                        StubApi(
                            scenario.path,
                            scenario.method,
                            scenario.status,
                            scenario.sourceProvider,
                            scenario.sourceRepository,
                            scenario.sourceRepositoryBranch,
                            scenario.specification,
                            scenario.serviceType
                        )
                    )
                }
            }
        }
    }

    override fun printUsageReport() {
        specmaticConfigPath?.let {
            val stubUsageReport = StubUsageReport(specmaticConfigPath, allSpecApis, logs )
            println("Saving Stub Usage Report json to $JSON_REPORT_PATH ...")
            val json = Json {
                encodeDefaults = false
            }
            val reportJson = json.encodeToString(stubUsageReport.generate())
            saveJsonFile(reportJson, JSON_REPORT_PATH, JSON_REPORT_FILE_NAME)
        }
    }
}

class CouldNotParseRequest(val innerException: Throwable): Exception(exceptionCauseMessage(innerException))

internal suspend fun ktorHttpRequestToHttpRequest(call: ApplicationCall): HttpRequest {
    try {
        val (body, formFields, multiPartFormData) = bodyFromCall(call)

        val requestHeaders = call.request.headers.toMap().mapValues { it.value[0] }

        return HttpRequest(
            method = call.request.httpMethod.value,
            path = call.request.path(),
            headers = requestHeaders,
            body = body,
            queryParams = toParams(call.request.queryParameters),
            formFields = formFields,
            multiPartFormData = multiPartFormData
        )
    } catch(e: Throwable) {
        throw CouldNotParseRequest(e)
    }
}

private suspend fun bodyFromCall(call: ApplicationCall): Triple<Value, Map<String, String>, List<MultiPartFormDataValue>> {
    return when {
        call.request.httpMethod == HttpMethod.Get -> Triple(EmptyString, emptyMap(), emptyList())
        call.request.contentType().match(ContentType.Application.FormUrlEncoded) -> Triple(
            EmptyString,
            call.receiveParameters().toMap().mapValues { (_, values) -> values.first() },
            emptyList()
        )
        call.request.isMultipart() -> {
            val multiPartData = call.receiveMultipart()
            val boundary = call.request.contentType().parameter("boundary") ?: "boundary"

            val parts = multiPartData.readAllParts().map {
                when (it) {
                    is PartData.FileItem -> {
                        val content = it.provider().asStream().use { inputStream ->
                            MultiPartContent(inputStream.readBytes())
                        }
                        MultiPartFileValue(
                            it.name ?: "",
                            it.originalFileName ?: "",
                            it.contentType?.let { contentType -> "${contentType.contentType}/${contentType.contentSubtype}" },
                            null,
                            content,
                            boundary
                        )
                    }
                    is PartData.FormItem -> {
                        MultiPartContentValue(
                            it.name ?: "",
                            StringValue(it.value),
                            boundary,
                            specifiedContentType = it.contentType?.let { contentType -> "${contentType.contentType}/${contentType.contentSubtype}" }
                        )
                    }
                    is PartData.BinaryItem -> {
                        val content = it.provider().asStream().use { input ->
                            val output = ByteArrayOutputStream()
                            input.copyTo(output)
                            output.toString()
                        }

                        MultiPartContentValue(
                            it.name ?: "",
                            StringValue(content),
                            boundary,
                            specifiedContentType = it.contentType?.let { contentType -> "${contentType.contentType}/${contentType.contentSubtype}" }
                        )
                    }
                    else -> {
                        throw UnsupportedOperationException("Unhandled PartData")
                    }
                }
            }

            Triple(EmptyString, emptyMap(), parts)
        }
        else -> Triple(parsedValue(receiveText(call)), emptyMap(), emptyList())
    }
}

suspend fun receiveText(call: ApplicationCall): String {
    return if(call.request.contentCharset() == null) {
            val byteArray: ByteArray = call.receive()
            String(byteArray, Charset.forName("UTF-8"))
        } else {
            call.receiveText()
        }
}

internal fun toParams(queryParameters: Parameters) = queryParameters.toMap().mapValues { it.value.first() }

internal suspend fun respondToKtorHttpResponse(call: ApplicationCall, httpResponse: HttpResponse, delayInSeconds: Int? = null) {
    val contentType = httpResponse.headers["Content-Type"] ?: httpResponse.body.httpContentType
    val textContent = TextContent(
        httpResponse.body.toStringLiteral(),
        ContentType.parse(contentType),
        HttpStatusCode.fromValue(httpResponse.status)
    )

    val headersControlledByEngine = listOfExcludedHeaders().map { it.lowercase() }
    for ((name, value) in httpResponse.headers.filterNot { it.key.lowercase() in headersControlledByEngine }) {
        call.response.headers.append(name, value)
    }

    if (delayInSeconds != null) {
        delay(delayInSeconds * 1000L)
    }

    call.respond(textContent)
}

fun getHttpResponse(
    httpRequest: HttpRequest,
    features: List<Feature>,
    threadSafeStubs: ThreadSafeListOfStubs,
    threadSafeStubQueue: ThreadSafeListOfStubs,
    strictMode: Boolean,
    passThroughTargetBase: String = "",
    httpClientFactory: HttpClientFactory? = null
): HttpStubResponse {
    return try {
        val (matchResults, stubResponse) = stubbedResponse(threadSafeStubs, threadSafeStubQueue, httpRequest)

        stubResponse
            ?: if (httpClientFactory != null && passThroughTargetBase.isNotBlank()) {
                passThroughResponse(httpRequest, passThroughTargetBase, httpClientFactory)
            } else {
                if (strictMode)
                    HttpStubResponse(strictModeHttp400Response(httpRequest, matchResults))
                else
                    fakeHttpResponse(features, httpRequest)
            }
    } finally {
        features.forEach { feature ->
            feature.clearServerState()
        }
    }
}

const val SPECMATIC_SOURCE_HEADER = "X-$APPLICATION_NAME-Source"

fun passThroughResponse(
    httpRequest: HttpRequest,
    passThroughUrl: String,
    httpClientFactory: HttpClientFactory
): HttpStubResponse {
    val response = httpClientFactory.client(passThroughUrl).execute(httpRequest)
    return HttpStubResponse(response.copy(headers = response.headers.plus(SPECMATIC_SOURCE_HEADER to "proxy")))
}

object StubAndRequestMismatchMessages : MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Stub expected $expected but request contained $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named $keyName in the request was not in the stub"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named $keyName in the stub was not found in the request"
    }
}

private fun stubbedResponse(
    threadSafeStubs: ThreadSafeListOfStubs,
    threadSafeStubQueue: ThreadSafeListOfStubs,
    httpRequest: HttpRequest
): Pair<List<Pair<Result, HttpStubData>>, HttpStubResponse?> {

    val (mock, matchResults) = stubThatMatchesRequest(threadSafeStubQueue, threadSafeStubs, httpRequest)

    val stubResponse = mock?.let {
        val softCastResponse = it.softCastResponseToXML(httpRequest).response
        HttpStubResponse(softCastResponse, it.delayInSeconds, it.contractPath)
    }

    return Pair(matchResults, stubResponse)
}

private fun stubThatMatchesRequest(
    threadSafeStubQueue: ThreadSafeListOfStubs,
    threadSafeStubs: ThreadSafeListOfStubs,
    httpRequest: HttpRequest
): Pair<HttpStubData?, List<Pair<Result, HttpStubData>>> {
    val queueMatchResults: List<Pair<Result, HttpStubData>> = threadSafeStubQueue.matchResults { stubs ->
        stubs.map {
            val (requestPattern, _, resolver) = it
            Pair(
                requestPattern.matches(
                    httpRequest,
                    resolver.disableOverrideUnexpectedKeycheck().copy(mismatchMessages = StubAndRequestMismatchMessages),
                    requestBodyReqex = it.requestBodyRegex
                ), it
            )
        }
    }

    val queueMock = queueMatchResults.findLast { (result, _) -> result is Result.Success }
    if(queueMock != null) {
        threadSafeStubQueue.remove(queueMock.second)
        return Pair(queueMock.second, queueMatchResults)
    }

    val listMatchResults: List<Pair<Result, HttpStubData>> = threadSafeStubs.matchResults { stubs ->
        stubs.map {
            val (requestPattern, _, resolver) = it
            Pair(
                requestPattern.matches(
                    httpRequest,
                    resolver.disableOverrideUnexpectedKeycheck().copy(mismatchMessages = StubAndRequestMismatchMessages),
                    requestBodyReqex = it.requestBodyRegex
                ), it
            )
        }
    }

    val mock = listMatchResults.find { (result, _) -> result is Result.Success }

    return Pair(mock?.second, listMatchResults)
}

object ContractAndRequestsMismatch : MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Contract expected $expected but request contained $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named $keyName in the request was not in the contract"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "${
            keyLabel.lowercase().capitalizeFirstChar()
        } named $keyName in the contract was not found in the request"
    }
}

private fun fakeHttpResponse(features: List<Feature>, httpRequest: HttpRequest): HttpStubResponse {
    data class ResponseDetails(val feature: Feature, val successResponse: ResponseBuilder?, val results: Results)

    val responses: List<ResponseDetails> = features.asSequence().map { feature ->
        feature.stubResponse(httpRequest, ContractAndRequestsMismatch).let {
            ResponseDetails(feature, it.first, it.second)
        }
    }.toList()

    return when (val fakeResponse = responses.find { it.successResponse != null }) {
        null -> {
            val failureResults = responses.filter { it.successResponse == null }.map { it.results }

            val httpFailureResponse = failureResults.reduce { first, second ->
                first.plus(second)
            }.withoutFluff().generateErrorHttpResponse(httpRequest)

            HttpStubResponse(httpFailureResponse)
        }
        else -> HttpStubResponse(
            fakeResponse.successResponse?.build()?.withRandomResultHeader()!!,
            contractPath = fakeResponse.feature.path,
            feature = fakeResponse.feature,
            scenario = fakeResponse.successResponse.scenario
        )
    }
}

private fun strictModeHttp400Response(
    httpRequest: HttpRequest,
    matchResults: List<Pair<Result, HttpStubData>>
): HttpResponse {
    val failureResults = matchResults.map { it.first }

    val results = Results(failureResults).withoutFluff()
    return HttpResponse(
        400,
        headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"),
        body = StringValue("STRICT MODE ON\n\n${results.strictModeReport(httpRequest)}")
    )
}

fun stubResponse(
    httpRequest: HttpRequest,
    contractInfo: List<Pair<Feature, List<ScenarioStub>>>,
    stubs: StubDataItems
): HttpResponse {
    return try {
        when (val mock = stubs.http.find { (requestPattern, _, resolver) ->
            requestPattern.matches(httpRequest, resolver.disableOverrideUnexpectedKeycheck()) is Result.Success
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
        mocks.map { mock ->
            feature.matchingStub(mock, ContractAndStubMismatchMessages)
        }
    }
}

fun badRequest(errorMessage: String?): HttpResponse {
    return HttpResponse(HttpStatusCode.BadRequest.value, errorMessage, mapOf(SPECMATIC_RESULT_HEADER to "failure"))
}

fun internalServerError(errorMessage: String?): HttpResponse {
    return HttpResponse(HttpStatusCode.InternalServerError.value, errorMessage, mapOf(SPECMATIC_RESULT_HEADER to "failure"))
}

internal fun httpResponseLog(response: HttpResponse): String =
    "${response.toLogString("<- ")}\n<< Response At ${Date()} == "

internal fun httpRequestLog(httpRequest: HttpRequest): String =
    ">> Request Start At ${Date()}\n${httpRequest.toLogString("-> ")}"

fun endPointFromHostAndPort(host: String, port: Int?, keyData: KeyData?): String {
    val protocol = when (keyData) {
        null -> "http"
        else -> "https"
    }

    val computedPortString = when (port) {
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

internal fun isSseExpectationCreation(httpRequest: HttpRequest) =
    isPath(httpRequest.path, "sse-expectations") && httpRequest.method == "POST"

internal fun isStateSetupRequest(httpRequest: HttpRequest): Boolean =
    isPath(httpRequest.path, "state") && httpRequest.method == "POST"

fun softCastResponseToXML(mockResponse: HttpStubData): HttpStubData =
    mockResponse.copy(response = mockResponse.response.copy(body = softCastValueToXML(mockResponse.response.body)))

fun softCastValueToXML(body: Value): Value {
    return when (body) {
        is StringValue -> try {
            toXMLNode(body.string)
        } catch (e: Throwable) {
            body
        }
        else -> body
    }
}

fun stringToMockScenario(text: Value): ScenarioStub {
    val mockSpec: Map<String, Value> =
        jsonStringToValueMap(text.toStringLiteral()).also {
            validateMock(it)
        }

    return mockFromJSON(mockSpec)
}

data class SseEvent(val data: String? = "", val event: String? = null, val id: String? = null, val bufferIndex: Int? = null)

suspend fun ApplicationCall.respondSse(events: ReceiveChannel<SseEvent>, sseBuffer: SSEBuffer, httpRequest: HttpRequest) {
    response.cacheControl(CacheControl.NoCache(null))

    respondTextWriter(contentType = ContentType.Text.EventStream) {
        logger.log("Writing out an initial response for subscription to ${httpRequest.path!!}")
        withContext(Dispatchers.IO) {
            write("\n")
            flush()
        }

        logger.log("Writing out buffered events for subscription to ${httpRequest.path}")
        sseBuffer.write(this)

        logger.log("Awaiting events...")
        for (event in events) {
            sseBuffer.add(event)
            logger.log("Writing out event for subscription to ${httpRequest.path}")
            logger.log("Event details: $event")

            writeEvent(event, this)
        }
    }
}

fun writeEvent(event: SseEvent, writer: Writer) {
    if (event.id != null) {
        writer.write("id: ${event.id}\n")
    }
    if (event.event != null) {
        writer.write("event: ${event.event}\n")
    }
    if(event.data != null) {
        for (dataLine in event.data.lines()) {
            writer.write("data: $dataLine\n")
        }
    }

    writer.write("\n")
    writer.flush()
}