package io.specmatic.stub

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.CORS
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.specmatic.core.*
import io.specmatic.core.log.*
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.parsedValue
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.core.route.modules.HealthCheckModule.Companion.isHealthCheckRequest
import io.specmatic.core.utilities.*
import io.specmatic.core.value.*
import io.specmatic.mock.*
import io.specmatic.stub.report.*
import io.specmatic.test.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Writer
import java.nio.charset.Charset
import java.util.*
import kotlin.text.toCharArray

class HttpStub(
    private val features: List<Feature>,
    rawHttpStubs: List<HttpStubData> = emptyList(),
    host: String = "127.0.0.1",
    port: Int = 9000,
    private val log: (event: LogMessage) -> Unit = dontPrintToConsole,
    private val strictMode: Boolean = false,
    keyData: KeyData? = null,
    val passThroughTargetBase: String = "",
    val httpClientFactory: HttpClientFactory = HttpClientFactory(),
    val workingDirectory: WorkingDirectory? = null,
    val specmaticConfigPath: String? = null,
    private val timeoutMillis: Long = 0,
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

        fun setExpectation(
            stub: ScenarioStub,
            feature: Feature,
            mismatchMessages: MismatchMessages = ContractAndStubMismatchMessages
        ): Pair<Pair<Result.Success, List<HttpStubData>>?, NoMatchingScenario?> {
            try {
                val tier1Match = feature.matchingStub(
                    stub,
                    mismatchMessages
                )

                val matchedScenario = tier1Match.scenario ?: throw ContractException("Expected scenario after stub matched for:${System.lineSeparator()}${stub.toJSON()}")

                val stubWithSubstitutionsResolved = stub.resolveDataSubstitutions().map { scenarioStub ->
                    feature.matchingStub(scenarioStub, ContractAndStubMismatchMessages)
                }

                val stubData: List<HttpStubData> = stubWithSubstitutionsResolved.map {
                    softCastResponseToXML(
                        it
                    )
                }

                return Pair(Pair(Result.Success(), stubData), null)
            } catch (e: NoMatchingScenario) {
                return Pair(null, e)
            }
        }
    }

    private val specmaticConfig: SpecmaticConfig =
        if(specmaticConfigPath != null && File(specmaticConfigPath).exists())
            loadSpecmaticConfig(specmaticConfigPath)
        else
            SpecmaticConfig()

    private val threadSafeHttpStubs = ThreadSafeListOfStubs(staticHttpStubData(rawHttpStubs))

    private val requestHandlers: MutableList<RequestHandler> = mutableListOf()

    //used by graphql / plugins
    fun registerHandler(requestHandler: RequestHandler) {
        requestHandlers.add(requestHandler)
    }

    private fun staticHttpStubData(rawHttpStubs: List<HttpStubData>): MutableList<HttpStubData> {
        val staticStubs = rawHttpStubs.filter { it.stubToken == null }

        val stubsFromSpecificationExamples: List<HttpStubData> = features.map { feature ->
            feature.stubsFromExamples.entries.map { (exampleName, examples) ->
                examples.mapNotNull { (request, response) ->
                    try {
                        val stubData: HttpStubData =
                            feature.matchingStub(request, response, ExamplesAsExpectationsMismatch(exampleName))

                        if (stubData.matchFailure) {
                            logger.newLine()
                            logger.log(stubData.response.body.toStringLiteral())
                            null
                        } else {
                            stubData
                        }
                    } catch (e: Throwable) {
                        logger.newLine()

                        when (e) {
                            is ContractException -> {
                                logger.log(e)
                                null
                            }
                            is NoMatchingScenario -> {
                                logger.log(e, "[Example $exampleName]")
                                null
                            }
                            else -> {
                                logger.log(e, "[Example $exampleName]")
                                throw e
                            }
                        }
                    }
                }
            }
        }.flatten().flatten()

        return staticStubs.plus(stubsFromSpecificationExamples).toMutableList()
    }

    private val threadSafeHttpStubQueue =
        ThreadSafeListOfStubs(rawHttpStubs.filter { it.stubToken != null }.reversed().toMutableList())

    private val _logs: MutableList<StubEndpoint> = Collections.synchronizedList(ArrayList())
    private val _allEndpoints: List<StubEndpoint> = extractALlEndpoints()

    val logs: List<StubEndpoint> get() = _logs.toList()
    val allEndpoints: List<StubEndpoint> get() = _allEndpoints.toList()


    val stubCount: Int
        get() {
            return threadSafeHttpStubs.size
        }

    val transientStubCount: Int
        get() {
            return threadSafeHttpStubQueue.size
        }

    val endPoint = endPointFromHostAndPort(host, port, keyData)

    private val stubCache = StubCache()

    override val client = HttpClient(this.endPoint)

    private val sseBuffer: SSEBuffer = SSEBuffer()

    private val broadcastChannels: Vector<BroadcastChannel<SseEvent>> = Vector(50, 10)

    private val requestInterceptors: MutableList<RequestInterceptor> = mutableListOf()

    private val responseInterceptors: MutableList<ResponseInterceptor> = mutableListOf()

    fun registerRequestInterceptor(requestInterceptor: RequestInterceptor) {
        requestInterceptors.add(requestInterceptor)
    }

    fun registerResponseInterceptor(responseInterceptor: ResponseInterceptor) {
        responseInterceptors.add(responseInterceptor)
    }

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
                    val rawHttpRequest = ktorHttpRequestToHttpRequest(call)
                    httpLogMessage.addRequest(rawHttpRequest)

                    if(rawHttpRequest.isHealthCheckRequest()) return@intercept

                    val httpRequest = requestInterceptors.fold(rawHttpRequest) { request, requestInterceptor ->
                        requestInterceptor.interceptRequest(request) ?: request
                    }

                    val responseFromRequestHandler = requestHandlers.map {
                        it.handleRequest(httpRequest)
                    }.filterNotNull().firstOrNull()

                    val httpStubResponse: HttpStubResponse = when {
                        isFetchLogRequest(httpRequest) -> handleFetchLogRequest()
                        isFetchLoadLogRequest(httpRequest) -> handleFetchLoadLogRequest()
                        isFetchContractsRequest(httpRequest) -> handleFetchContractsRequest()
                        responseFromRequestHandler != null -> responseFromRequestHandler
                        isExpectationCreation(httpRequest) -> handleExpectationCreationRequest(httpRequest)
                        isSseExpectationCreation(httpRequest) -> handleSseExpectationCreationRequest(httpRequest)
                        isStateSetupRequest(httpRequest) -> handleStateSetupRequest(httpRequest)
                        isFlushTransientStubsRequest(httpRequest) -> handleFlushTransientStubsRequest(httpRequest)
                        else -> serveStubResponse(httpRequest)
                    }

                    val httpResponse = responseInterceptors.fold(httpStubResponse.response) { response, responseInterceptor ->
                        responseInterceptor.interceptResponse(httpRequest, response) ?: response
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

                            close(
                                events,
                                channel,
                                "Events handle was already closed after handling all events",
                                "Channel was already handled after handling all events"
                            )
                        } catch (e: Throwable) {
                            logger.log(e, "Exception in the SSE module")

                            broadcastChannels.remove(broadcastChannel)

                            close(
                                events,
                                channel,
                                "Events handle threw an exception on closing",
                                "Channel through an exception on closing"
                            )
                        }
                    } else {
                        val updatedHttpStubResponse = httpStubResponse.copy(response = httpResponse)
                        respondToKtorHttpResponse(call, updatedHttpStubResponse.response, updatedHttpStubResponse.delayInMilliSeconds, specmaticConfig)
                        httpLogMessage.addResponse(updatedHttpStubResponse)
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

            configureHealthCheckModule()
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

    fun serveStubResponse(httpRequest: HttpRequest): HttpStubResponse {
        val result: StubbedResponseResult = getHttpResponse(
            httpRequest,
            features,
            threadSafeHttpStubs,
            threadSafeHttpStubQueue,
            strictMode,
            passThroughTargetBase,
            httpClientFactory,
            specmaticConfig,
            stubCache
        )

        result.log(_logs, httpRequest)

        return result.response
    }

    private fun handleFlushTransientStubsRequest(httpRequest: HttpRequest): HttpStubResponse {
        val token = httpRequest.path?.removePrefix("/_specmatic/$TRANSIENT_MOCK/")

        threadSafeHttpStubQueue.removeWithToken(token)

        return HttpStubResponse(HttpResponse.OK)
    }

    private fun isFlushTransientStubsRequest(httpRequest: HttpRequest): Boolean {
        return httpRequest.method?.toLowerCasePreservingASCIIRules() == "delete" && httpRequest.path?.startsWith("/_specmatic/$TRANSIENT_MOCK/") == true
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
        }.let { request ->
            val requestHeaders = call.request.headers.toMap().mapValues { it.value[0] }
            request.copy(headers = requestHeaders)
        }.let {
            val queryParams = toParams(call.request.queryParameters)
            it.copy(queryParams = QueryParameters(paramPairs = queryParams))
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
        HttpStubResponse(HttpResponse.ok(StringValue(LogTail.getSnapshot())))

    private fun handleFetchContractsRequest(): HttpStubResponse =
        HttpStubResponse(HttpResponse.ok(StringValue(features.joinToString("\n") { it.name })))

    private fun handleFetchLogRequest(): HttpStubResponse =
        HttpStubResponse(HttpResponse.ok(StringValue(LogTail.getString())))

    private fun handleExpectationCreationRequest(httpRequest: HttpRequest): HttpStubResponse {
        return try {
            if (httpRequest.body.toStringLiteral().isEmpty())
                throw ContractException("Expectation payload was empty")

            val mock: ScenarioStub = stringToMockScenario(httpRequest.body)
            val stub: HttpStubData = setExpectation(mock).first()

            HttpStubResponse(HttpResponse.OK, contractPath = stub.contractPath)
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

            if (sseEvent == null) {
                logger.debug("No Sse Event was found in the request:\n${httpRequest.toLogString("  ")}")
            } else if (sseEvent.bufferIndex == null) {
                logger.debug("Broadcasting event: $sseEvent")

                for (channel in broadcastChannels) {
                    channel.send(sseEvent)
                }
            } else {
                logger.debug("Adding event to buffer: $sseEvent")
                sseBuffer.add(sseEvent)
            }

            HttpStubResponse(HttpResponse.OK, contractPath = "")
        } catch (e: ContractException) {
            HttpStubResponse(
                HttpResponse(
                    status = 400,
                    headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"),
                    body = exceptionCauseMessage(e)
                )
            )
        } catch (e: Throwable) {
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

    fun setExpectation(stub: ScenarioStub): List<HttpStubData> {
        val results = features.asSequence().map { feature -> setExpectation(stub, feature) }

        val result: Pair<Pair<Result.Success, List<HttpStubData>>?, NoMatchingScenario?>? = results.find { it.first != null }
        val firstResult: Pair<Result.Success, List<HttpStubData>>? = result?.first

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
                val stubData = firstResult.second.map { it.copy(requestBodyRegex = requestBodyRegex) }
                val resultWithRequestBodyRegex = stubData.map { Pair(firstResult.first, it) }

                if (stub.stubToken != null) {
                    resultWithRequestBodyRegex.forEach {
                        threadSafeHttpStubQueue.addToStub(it, stub)
                    }

                } else {
                    resultWithRequestBodyRegex.forEach {
                        threadSafeHttpStubs.addToStub(it, stub)
                    }
                }
            }
        }

        return firstResult.second
    }

    private fun parseRegex(regex: String?): Regex? {
        return regex?.let {
            try {
                Regex(it)
            } catch (e: Throwable) {
                throw ContractException("Couldn't parse regex $regex", exceptionCause = e)
            }
        }
    }

    override fun close() {
        server.stop(gracePeriodMillis = timeoutMillis, timeoutMillis = timeoutMillis)
        printUsageReport()
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

    private fun extractALlEndpoints(): List<StubEndpoint> {
        return features.map {
            it.scenarios.map { scenario ->
                if (scenario.isA2xxScenario()) {
                    StubEndpoint(
                        scenario.path,
                        scenario.method,
                        scenario.status,
                        scenario.sourceProvider,
                        scenario.sourceRepository,
                        scenario.sourceRepositoryBranch,
                        scenario.specification,
                        scenario.serviceType
                    )
                } else {
                    null
                }
            }
        }.flatten().filterNotNull()
    }

    private fun printUsageReport() {
        specmaticConfigPath?.let {
            val stubUsageReport = StubUsageReport(specmaticConfigPath, _allEndpoints, _logs)
            println("Saving Stub Usage Report json to $JSON_REPORT_PATH ...")
            val json = Json {
                encodeDefaults = false
            }
            val generatedReport = stubUsageReport.generate()
            val reportJson: String = File(JSON_REPORT_PATH).resolve(JSON_REPORT_FILE_NAME).let { reportFile ->
                if (reportFile.exists()) {
                    try {
                        val existingReport = Json.decodeFromString<StubUsageReportJson>(reportFile.readText())
                        json.encodeToString(generatedReport.merge(existingReport))
                    } catch (exception: SerializationException) {
                        logger.log("The existing report file is not a valid Stub Usage Report. ${exception.message}")
                        json.encodeToString(generatedReport)
                    }
                } else {
                    json.encodeToString(generatedReport)
                }
            }

            saveJsonFile(reportJson, JSON_REPORT_PATH, JSON_REPORT_FILE_NAME)
        }
    }
}

class CouldNotParseRequest(innerException: Throwable) : Exception(exceptionCauseMessage(innerException))

internal suspend fun ktorHttpRequestToHttpRequest(call: ApplicationCall): HttpRequest {
    try {
        val (body, formFields, multiPartFormData) = bodyFromCall(call)

        val requestHeaders = call.request.headers.toMap().mapValues { it.value[0] }

        return HttpRequest(
            method = call.request.httpMethod.value,
            path = urlDecodePathSegments(call.request.path()),
            headers = requestHeaders,
            body = body,
            queryParams = QueryParameters(paramPairs = toParams(call.request.queryParameters)),
            formFields = formFields,
            multiPartFormData = multiPartFormData
        )
    } catch (e: Throwable) {
        throw CouldNotParseRequest(e)
    }
}

private suspend fun bodyFromCall(call: ApplicationCall): Triple<Value, Map<String, String>, List<MultiPartFormDataValue>> {
    return when {
        call.request.httpMethod == HttpMethod.Get -> if(call.request.headers.contains("Content-Type")) {
            Triple(parsedValue(receiveText(call)), emptyMap(), emptyList())
        } else {
            Triple(NoBodyValue, emptyMap(), emptyList())
        }

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

        else -> {
            if(call.request.headers.contains("Content-Type"))
                Triple(parsedValue(receiveText(call)), emptyMap(), emptyList())
            else
                Triple(NoBodyValue, emptyMap(), emptyList())
        }
    }
}

suspend fun receiveText(call: ApplicationCall): String {
    return if (call.request.contentCharset() == null) {
        val byteArray: ByteArray = call.receive()
        String(byteArray, Charset.forName("UTF-8"))
    } else {
        call.receiveText()
    }
}

//internal fun toParams(queryParameters: Parameters) = queryParameters.toMap().mapValues { it.value.first() }

internal fun toParams(queryParameters: Parameters): List<Pair<String, String>> =
    queryParameters.toMap().flatMap { (parameterName, parameterValues) ->
        parameterValues.map {
            parameterName to it
        }
    }

internal suspend fun respondToKtorHttpResponse(
    call: ApplicationCall,
    httpResponse: HttpResponse,
    delayInMilliSeconds: Long? = null,
    specmaticConfig: SpecmaticConfig? = null
) {
    val headersControlledByEngine = listOfExcludedHeaders().map { it.lowercase() }
    for ((name, value) in httpResponse.headers.filterNot { it.key.lowercase() in headersControlledByEngine }) {
        call.response.headers.append(name, value)
    }

    val delayInMs = delayInMilliSeconds ?: specmaticConfig?.stub?.delayInMilliseconds
    if (delayInMs != null) {
        delay(delayInMs)
    }

    val contentType = httpResponse.headers["Content-Type"] ?: httpResponse.body.httpContentType
    val responseBody = httpResponse.body.toStringLiteral()
    val status = HttpStatusCode.fromValue(httpResponse.status)

    if (contentType.isBlank()) {
        call.respond(object : OutgoingContent.NoContent() {
            override val status: HttpStatusCode = HttpStatusCode.fromValue(httpResponse.status)
        })
        return
    }

    call.respond(TextContent(responseBody, ContentType.parse(contentType), status))
}

fun getHttpResponse(
    httpRequest: HttpRequest,
    features: List<Feature>,
    threadSafeStubs: ThreadSafeListOfStubs,
    threadSafeStubQueue: ThreadSafeListOfStubs,
    strictMode: Boolean,
    passThroughTargetBase: String = "",
    httpClientFactory: HttpClientFactory? = null,
    specmaticConfig: SpecmaticConfig = SpecmaticConfig(),
    stubCache: StubCache = StubCache()
): StubbedResponseResult {
    try {
        if(specmaticConfig.stub.stateful == true) {
            return cachedHttpResponse(features, httpRequest, specmaticConfig, stubCache)
        }

        val (matchResults, matchingStubResponse) = stubbedResponse(threadSafeStubs, threadSafeStubQueue, httpRequest)
        if(matchingStubResponse != null) {
            val (httpStubResponse, httpStubData) = matchingStubResponse
            return FoundStubbedResponse(
                httpStubResponse.resolveSubstitutions(
                    httpRequest,
                    if (httpStubData.partial != null) httpStubData.partial.request else httpStubData.originalRequest
                        ?: httpRequest,
                    httpStubData.data,
                )
            )
        }
        if (httpClientFactory != null && passThroughTargetBase.isNotBlank()) {
            return NotStubbed(
                passThroughResponse(
                    httpRequest,
                    passThroughTargetBase,
                    httpClientFactory
                )
            )
        }
        if(strictMode) return NotStubbed(HttpStubResponse(strictModeHttp400Response(httpRequest, matchResults)))

        return fakeHttpResponse(features, httpRequest, specmaticConfig)
    } finally {
        features.forEach { feature -> feature.clearServerState() }
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
): Pair<List<Pair<Result, HttpStubData>>, Pair<HttpStubResponse, HttpStubData>?> {

    val (mock, matchResults) = stubThatMatchesRequest(threadSafeStubQueue, threadSafeStubs, httpRequest)

    val stubResponse = mock?.let {
        val softCastResponse = it.softCastResponseToXML(httpRequest).response
        HttpStubResponse(
            softCastResponse,
            it.delayInMilliseconds,
            it.contractPath,
            examplePath = it.examplePath,
            feature = mock.feature,
            scenario = mock.scenario
        ) to it
    }

    return Pair(matchResults, stubResponse)
}

private fun stubThatMatchesRequest(
    transientStubs: ThreadSafeListOfStubs,
    nonTransientStubs: ThreadSafeListOfStubs,
    httpRequest: HttpRequest
): Pair<HttpStubData?, List<Pair<Result, HttpStubData>>> {
    return transientStubs.matchingTransientStub(httpRequest)
        ?: nonTransientStubs.matchingNonTransientStub(httpRequest)
}

fun isMissingData(e: Throwable?): Boolean {
    return when (e) {
        null -> false
        is MissingDataException -> true
        is ContractException -> isMissingData(e.exceptionCause)
        else -> false
    }
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

data class ResponseDetails(val feature: Feature, val successResponse: ResponseBuilder?, val results: Results)

private fun fakeHttpResponse(
    features: List<Feature>,
    httpRequest: HttpRequest,
    specmaticConfig: SpecmaticConfig = SpecmaticConfig()
): StubbedResponseResult {

    if (features.isEmpty())
       return NotStubbed(HttpStubResponse(HttpResponse(400, "No valid API specifications loaded")))

    val responses: List<ResponseDetails> = responseDetailsFrom(features, httpRequest)

    return when (val fakeResponse = responses.successResponse()) {
        null -> {
            val failureResults = responses.filter { it.successResponse == null }.map { it.results }

            val combinedFailureResult = failureResults.reduce { first, second ->
                first.plus(second)
            }.withoutFluff()

            val firstScenarioWith400Response = failureResults.flatMap { it.results }.filter {
                it is Result.Failure
                    && it.failureReason == null
                    && it.scenario?.let { it.status == 400 || it.status == 422 } == true
            }.map { it.scenario!! }.firstOrNull()

            if(firstScenarioWith400Response != null && specmaticConfig.stub.generative == true) {
                val httpResponse = (firstScenarioWith400Response as Scenario).generateHttpResponse(emptyMap())
                val updatedResponse: HttpResponse = dumpIntoFirstAvailableStringField(httpResponse, combinedFailureResult.report())

                FoundStubbedResponse(
                    HttpStubResponse(
                        updatedResponse,
                        contractPath = "",
                        feature = fakeResponse?.feature,
                        scenario = fakeResponse?.successResponse?.scenario
                    )
                )
            } else {
                val httpFailureResponse = combinedFailureResult.generateErrorHttpResponse(httpRequest)

                NotStubbed(HttpStubResponse(httpFailureResponse))
            }
        }

        else -> FoundStubbedResponse(
            HttpStubResponse(
                generateHttpResponseFrom(fakeResponse, httpRequest),
                contractPath = fakeResponse.feature.path,
                feature = fakeResponse.feature,
                scenario = fakeResponse.successResponse?.scenario
            )
        )
    }
}

private fun responseDetailsFrom(features: List<Feature>, httpRequest: HttpRequest): List<ResponseDetails> {
    return features.asSequence().map { feature ->
        feature.stubResponse(httpRequest, ContractAndRequestsMismatch).let {
            ResponseDetails(feature, it.first, it.second)
        }
    }.toList()
}

private fun List<ResponseDetails>.successResponse(): ResponseDetails? {
    return this.find { it.successResponse != null }
}

private fun generateHttpResponseFrom(fakeResponse: ResponseDetails, httpRequest: HttpRequest): HttpResponse {
    return fakeResponse.successResponse?.build(RequestContext(httpRequest))?.withRandomResultHeader()!!
}

private fun cachedHttpResponse(
    features: List<Feature>,
    httpRequest: HttpRequest,
    specmaticConfig: SpecmaticConfig = SpecmaticConfig(),
    stubCache: StubCache
): StubbedResponseResult {
    if (features.isEmpty())
        return NotStubbed(HttpStubResponse(HttpResponse(400, "No valid API specifications loaded")))

    val responses: List<ResponseDetails> = responseDetailsFrom(features, httpRequest)
    val fakeResponse = responses.successResponse()
        ?: return fakeHttpResponse(features, httpRequest, specmaticConfig)

    val generatedResponse = generateHttpResponseFrom(fakeResponse, httpRequest)
    val updatedResponse = cachedResponse(fakeResponse, httpRequest, stubCache) ?: generatedResponse

    return FoundStubbedResponse(
        HttpStubResponse(
            updatedResponse,
            contractPath = fakeResponse.feature.path,
            feature = fakeResponse.feature,
            scenario = fakeResponse.successResponse?.scenario
        )
    )
}

private fun cachedResponse(fakeResponse: ResponseDetails, httpRequest: HttpRequest, stubCache: StubCache): HttpResponse? {
    val scenario = fakeResponse.successResponse?.scenario
    val method = scenario?.method

    val generatedResponse = generateHttpResponseFrom(fakeResponse, httpRequest)
    val pathSegments = httpRequest.path?.split("/")?.filter { it.isNotBlank() }.orEmpty()

    val unsupportedResponseBodyForCaching =
        (generatedResponse.body is JSONObjectValue ||
                (method == "DELETE" && pathSegments.size > 1) ||
                (method == "GET" &&
                        generatedResponse.body is JSONArrayValue &&
                        generatedResponse.body.list.firstOrNull() is JSONObjectValue)).not()

    if(unsupportedResponseBodyForCaching) return null

    val resourcePath = "/${pathSegments.first()}"
    val resourceId = pathSegments.last()
    val resourceIdKey = resourceIdKeyFrom(scenario?.httpRequestPattern)

    if(method == "POST") return generateAndCachePostResponse(generatedResponse, httpRequest, stubCache, resourcePath)

    if(method == "PATCH" && pathSegments.size > 1) {
        return generateAndCachePatchResponse(generatedResponse, httpRequest, stubCache, resourcePath, resourceIdKey, resourceId)
    }

    if(method == "GET" && pathSegments.size == 1) {
        val responseBody = stubCache.findAllResponsesFor(resourcePath)
        return generatedResponse.withUpdated(responseBody)
    }

    if(method == "GET" && pathSegments.size > 1) {
        val responseBody = stubCache.findResponseFor(resourcePath, resourceIdKey, resourceId)?.responseBody
            ?: return HttpResponse(404, "Resource with resourceId '$resourceId' not found")
        return generatedResponse.withUpdated(responseBody)
    }

    if(method == "DELETE" && pathSegments.size > 1) {
        stubCache.deleteResponse(resourcePath, resourceIdKey, resourceId)
        return generatedResponse
    }

    return null
}

private fun generateAndCachePostResponse(
    generatedResponse: HttpResponse,
    httpRequest: HttpRequest,
    stubCache: StubCache,
    resourcePath: String
): HttpResponse? {
    if(generatedResponse.body !is JSONObjectValue || httpRequest.body !is JSONObjectValue)
        return null

    val responseBody = generatedResponse.body
    val responseBodyWithValuesFromRequest = responseBody.copy(
        jsonObject = patchValuesFromRequestIntoResponse(httpRequest.body, responseBody)
    )
    stubCache.addResponse(resourcePath, responseBodyWithValuesFromRequest)
    return generatedResponse.withUpdated(responseBodyWithValuesFromRequest)
}

private fun generateAndCachePatchResponse(
    generatedResponse: HttpResponse,
    httpRequest: HttpRequest,
    stubCache: StubCache,
    resourcePath: String,
    resourceIdKey: String,
    resourceId: String
): HttpResponse? {
    if(httpRequest.body !is JSONObjectValue) return null

    val cachedResponse = stubCache.findResponseFor(resourcePath, resourceIdKey, resourceId)

    val responseBody = cachedResponse?.responseBody ?: return null

    val updatedResponseBody = responseBody.copy(
        jsonObject = patchValuesFromRequestIntoResponse(httpRequest.body, responseBody)
    )
    stubCache.updateResponse(resourcePath, updatedResponseBody, resourceIdKey, resourceId)

    return generatedResponse.withUpdated(updatedResponseBody)
}

private fun patchValuesFromRequestIntoResponse(requestBody: JSONObjectValue, responseBody: JSONObjectValue): Map<String, Value> {
    return responseBody.jsonObject.mapValues { (key, value) ->
        val patchValueFromRequest = requestBody.jsonObject.entries.firstOrNull {
            it.key == key
        }?.value ?: return@mapValues value

        if(patchValueFromRequest::class.java == value::class.java) return@mapValues patchValueFromRequest
        value
    }
}

private fun resourceIdKeyFrom(httpRequestPattern: HttpRequestPattern?): String {
    return httpRequestPattern?.getPathSegmentPatterns()?.last()?.key.orEmpty()
}

private fun HttpResponse.withUpdated(body: Value): HttpResponse {
    return this.copy(body = body)
}

fun dumpIntoFirstAvailableStringField(httpResponse: HttpResponse, stringValue: String): HttpResponse {
    val responseBody = httpResponse.body

    if(responseBody !is JSONObjectValue)
        return httpResponse

    val newBody = dumpIntoFirstAvailableStringField(responseBody, stringValue)

    return httpResponse.copy(body = newBody)
}

fun dumpIntoFirstAvailableStringField(jsonObjectValue: JSONObjectValue, stringValue: String): JSONObjectValue {
    val key = jsonObjectValue.jsonObject.keys.find { key ->
        key == "message" && jsonObjectValue.jsonObject[key] is StringValue
    } ?: jsonObjectValue.jsonObject.keys.find { key ->
        jsonObjectValue.jsonObject[key] is StringValue
    }

    if(key != null)
        return jsonObjectValue.copy(
            jsonObject = jsonObjectValue.jsonObject.plus(
                key to StringValue(stringValue)
            )
        )

    val newMap = jsonObjectValue.jsonObject.mapValues { (key, value) ->
        when (value) {
            is JSONObjectValue -> {
                dumpIntoFirstAvailableStringField(value, stringValue)
            }

            is JSONArrayValue -> {
                dumpIntoFirstAvailableStringField(value, stringValue)
            }

            else -> {
                value
            }
        }
    }

    return jsonObjectValue.copy(jsonObject = newMap)
}

fun dumpIntoFirstAvailableStringField(jsonArrayValue: JSONArrayValue, stringValue: String): JSONArrayValue {
    val indexOfFirstStringValue = jsonArrayValue.list.indexOfFirst { it is StringValue }

    if(indexOfFirstStringValue >= 0) {
        val mutableList = jsonArrayValue.list.toMutableList()
        mutableList.add(indexOfFirstStringValue, StringValue(stringValue))

        return jsonArrayValue.copy(
            list = mutableList
        )
    }

    val newList = jsonArrayValue.list.map { value ->
        when (value) {
            is JSONObjectValue -> {
                dumpIntoFirstAvailableStringField(value, stringValue)
            }

            is JSONArrayValue -> {
                dumpIntoFirstAvailableStringField(value, stringValue)
            }

            else -> {
                value
            }
        }
    }

    return jsonArrayValue.copy(list = newList)
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
        body = StringValue("STRICT MODE ON${System.lineSeparator()}${System.lineSeparator()}${results.strictModeReport(httpRequest)}")
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
    return contractInfo.flatMap { (feature, examples) ->
        examples.map { example ->
            feature.matchingStub(example, ContractAndStubMismatchMessages) to example
        }.flatMap { (stubData, example) ->
            val examplesWithDataSubstitutionsResolved = try {
                example.resolveDataSubstitutions()
            } catch(e: Throwable) {
                println()
                logger.log("    Error resolving template data for example ${example.filePath}")
                logger.log("    " + exceptionCauseMessage(e))
                throw e
            }

            examplesWithDataSubstitutionsResolved.map {
                feature.matchingStub(it, ContractAndStubMismatchMessages)
            }
        }
    }
}

fun badRequest(errorMessage: String?): HttpResponse {
    return HttpResponse(HttpStatusCode.BadRequest.value, errorMessage, mapOf(SPECMATIC_RESULT_HEADER to "failure"))
}

fun internalServerError(errorMessage: String?): HttpResponse {
    return HttpResponse(
        HttpStatusCode.InternalServerError.value,
        errorMessage,
        mapOf(SPECMATIC_RESULT_HEADER to "failure")
    )
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
    return path == "/_$APPLICATION_NAME_LOWER_CASE/$lastPart"
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

data class SseEvent(
    val data: String? = "",
    val event: String? = null,
    val id: String? = null,
    val bufferIndex: Int? = null
)

suspend fun ApplicationCall.respondSse(
    events: ReceiveChannel<SseEvent>,
    sseBuffer: SSEBuffer,
    httpRequest: HttpRequest
) {
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
    if (event.data != null) {
        for (dataLine in event.data.lines()) {
            writer.write("data: $dataLine\n")
        }
    }

    writer.write("\n")
    writer.flush()
}