package io.specmatic.core.examples.server

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.specmatic.conversions.ExampleFromFile
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.*
import io.specmatic.core.examples.server.ExamplesView.Companion.toTableRows
import io.specmatic.core.filters.ScenarioMetadataFilter
import io.specmatic.core.filters.ScenarioMetadataFilter.Companion.filterUsing
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.core.utilities.*
import io.specmatic.mock.NoMatchingScenario
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.HttpStub
import io.specmatic.stub.HttpStubData
import io.specmatic.test.ContractTest
import io.specmatic.test.TestInteractionsLog
import io.specmatic.test.TestInteractionsLog.combineLog
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

class ExamplesInteractiveServer(
    private val serverHost: String,
    private val serverPort: Int,
    private val testBaseUrl: String?,
    private val inputContractFile: File? = null,
    private val filterName: String,
    private val filterNotName: String,
    private val filter: List<String>,
    private val filterNot: List<String>,
    externalDictionaryFile: File? = null
) : Closeable {
    private var contractFileFromRequest: File? = null

    init {
        if(externalDictionaryFile != null) System.setProperty(SPECMATIC_STUB_DICTIONARY, externalDictionaryFile.path)
    }

    private fun getContractFile(): File {
        if(inputContractFile != null && inputContractFile.exists()) return inputContractFile
        if(contractFileFromRequest != null && contractFileFromRequest!!.exists()) return contractFileFromRequest!!
        throw ContractException("Invalid contract file provided to the examples interactive server")
    }

    private fun getServerHostPort(request: ExamplePageRequest? = null) : String {
        return request?.hostPort ?: "http://localhost:$serverPort"
    }

    private val environment = applicationEngineEnvironment {
        module {
            install(CORS) {
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowHeader(HttpHeaders.AccessControlAllowOrigin)
                allowHeader(HttpHeaders.ContentType)
                anyHost()
            }

            install(ContentNegotiation) {
                jackson {}
            }

            configureHealthCheckModule()
            routing {
                get("/_specmatic/examples") {
                    val contractFile = getContractFileOrBadRequest(call) ?: return@get
                    try {
                        respondWithExamplePageHtmlContent(contractFile, getServerHostPort(), call)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
                    }
                }

                post("/_specmatic/examples") {
                    val request = call.receive<ExamplePageRequest>()
                    contractFileFromRequest = File(request.contractFile)
                    val contractFile = getContractFileOrBadRequest(call) ?: return@post
                    try {
                        respondWithExamplePageHtmlContent(contractFile,getServerHostPort(request), call)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
                    }
                }

                post("/_specmatic/examples/generate") {
                    val contractFile = getContractFile()

                    try {
                        val request = call.receive<GenerateExampleRequest>()
                        val generatedExample = generate(
                            contractFile,
                            request.method,
                            request.path,
                            request.responseStatusCode,
                            request.contentType,
                        )

                        call.respond(HttpStatusCode.OK, GenerateExampleResponse.from(generatedExample))
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
                    }
                }

                post("/_specmatic/examples/validate") {
                    val request = call.receive<ValidateExampleRequest>()
                    try {
                        val contractFile = getContractFile()
                        val validationResultResponse = try {
                            val result = validateSingleExample(contractFile, File(request.exampleFile))
                            if(result.isSuccess())
                                ValidateExampleResponse(request.exampleFile)
                            else
                                ValidateExampleResponse(request.exampleFile, result.reportString())
                        } catch (e: FileNotFoundException) {
                            ValidateExampleResponse(request.exampleFile, e.message ?: "File not found")
                        } catch (e: ContractException) {
                            ValidateExampleResponse(request.exampleFile, exceptionCauseMessage(e))
                        } catch (e: Exception) {
                            ValidateExampleResponse(request.exampleFile, e.message ?: "An unexpected error occurred")
                        }
                        call.respond(HttpStatusCode.OK, validationResultResponse)
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An unexpected error occurred: ${e.message}"))
                    }
                }

                post("/_specmatic/v2/examples/validate") {
                    val request = call.receive<List<ValidateExampleRequest>>()
                    try {
                        val contractFile = getContractFile()

                        val examples = request.associate {
                            val exampleFilePath = it.exampleFile
                            exampleFilePath to listOf(ScenarioStub.readFromFile(File(exampleFilePath)))
                        }

                        val results = validateMultipleExamples(contractFile, examples = examples)

                        val validationResults = results.map { (exampleFilePath, result) ->
                            try {
                                result.throwOnFailure()
                                ValidateExampleResponseV2(
                                    ValidateExampleVerdict.SUCCESS,
                                    "The provided example is valid",
                                    exampleFilePath
                                )
                            } catch (e: ContractException) {
                                ValidateExampleResponseV2(
                                    ValidateExampleVerdict.FAILURE,
                                    exceptionCauseMessage(e),
                                    exampleFilePath
                                )
                            } catch (e: Exception) {
                                ValidateExampleResponseV2(
                                    ValidateExampleVerdict.FAILURE,
                                    e.message ?: "An unexpected error occurred",
                                    exampleFilePath
                                )
                            }
                        }

                        call.respond(HttpStatusCode.OK, validationResults)
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An unexpected error occurred: ${e.message}"))
                    }
                }

                get("/_specmatic/examples/content") {
                    val fileName = call.request.queryParameters["fileName"]
                    if(fileName == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request. Missing required query param named 'fileName'"))
                        return@get
                    }
                    val file = File(fileName)
                    if(file.exists().not() || file.extension != "json") {
                        val message = if(file.extension == "json") "The provided example file ${file.name} does not exist"
                        else "The provided example file ${file.name} is not a valid example file"
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to message))
                        return@get
                    }
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("content" to File(fileName).readText())
                    )
                }

                post ("/_specmatic/examples/test") {
                    if (testBaseUrl.isNullOrEmpty()) {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request, No Test Base URL provided via command-line"))
                    }

                    val request = call.receive<ExampleTestRequest>()
                    try {
                        val feature = OpenApiSpecification.fromFile(getContractFile().path).toFeature()

                        val contractTest = feature.createContractTestFromExampleFile(request.exampleFile).value

                        val (result, testLog) = testExample(contractTest, testBaseUrl)

                        call.respond(HttpStatusCode.OK, ExampleTestResponse(result, testLog, exampleFile = File(request.exampleFile)))
                    } catch (e: Throwable) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An unexpected error occurred: ${e.message}"))
                    }
                }
            }
        }
        connector {
            this.host = serverHost
            this.port = serverPort
        }
    }

    private val server: ApplicationEngine = embeddedServer(Netty, environment, configure = {
        this.requestQueueLimit = 1000
        this.callGroupSize = 5
        this.connectionGroupSize = 20
        this.workerGroupSize = 20
    })

    init {
        server.start()
    }

    override fun close() {
        server.stop(0, 0)
    }

    private suspend fun getContractFileOrBadRequest(call: ApplicationCall): File? {
        return try {
            getContractFile()
        } catch(e: ContractException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            return null
        }
    }

    private suspend fun respondWithExamplePageHtmlContent(contractFile: File, hostPort: String, call: ApplicationCall) {
        try {
            val html = getExamplePageHtmlContent(contractFile, hostPort)
            call.respondText(html, contentType = ContentType.Text.Html)
        } catch (e: Exception) {
            println(e)
            call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
        }
    }

    private fun getExamplePageHtmlContent(contractFile: File, hostPort: String): String {
        val feature = ScenarioFilter(filterName, filterNotName, filter, filterNot).filter(parseContractFileToFeature(contractFile))

        val endpoints = ExamplesView.getEndpoints(feature, getExamplesDirPath(contractFile))
        val tableRows = endpoints.toTableRows()

        return HtmlTemplateConfiguration.process(
            templateName = "examples/index.html",
            variables = mapOf(
                "tableRows" to tableRows,
                "contractFile" to contractFile.name,
                "contractFilePath" to contractFile.absolutePath,
                "hostPort" to hostPort,
                "hasExamples" to tableRows.any {it.example != null},
                "exampleDetails" to tableRows.transform(),
                "isTestMode" to (testBaseUrl != null)
            )
        )
    }

    private fun List<TableRow>.transform(): Map<String, Map<String, String?>> {
        return this.groupBy { it.uniqueKey }.mapValues { (_, keyGroup) ->
            keyGroup.associateBy({ it.example ?: "null" }, { it.exampleMismatchReason })
        }
    }

    class ScenarioFilter(filterName: String = "", filterNotName: String = "", filterClauses: List<String> = emptyList(), private val filterNotClauses: List<String> = emptyList()) {
        private val filter = filterClauses.joinToString(";")
        private val filterNot = filterNotClauses.joinToString(";")

        private val filterNameTokens = if(filterName.isNotBlank()) {
            filterName.trim().split(",").map { it.trim() }
        } else emptyList()

        private val filterNotNameTokens = if(filterNotName.isNotBlank()) {
            filterNotName.trim().split(",").map { it.trim() }
        } else emptyList()

        fun filter(feature: Feature): Feature {
            val scenariosFilteredByOlderSyntax = feature.scenarios.filter { scenario ->
                if(filterNameTokens.isNotEmpty()) {
                    filterNameTokens.any { name -> scenario.testDescription().contains(name) }
                } else true
            }.filter { scenario ->
                if(filterNotNameTokens.isNotEmpty()) {
                    filterNotNameTokens.none { name -> scenario.testDescription().contains(name) }
                } else true
            }

            val scenarioInclusionFilter = ScenarioMetadataFilter.from(filter)
            val scenarioExclusionFilter = ScenarioMetadataFilter.from(filterNot)

            val filteredScenarios = filterUsing(scenariosFilteredByOlderSyntax.asSequence(), scenarioInclusionFilter, scenarioExclusionFilter) {
                it.toScenarioMetadata()
            }.toList()


            return feature.copy(scenarios = filteredScenarios)
        }
    }

    companion object {
        private val exampleFileNamePostFixCounter = AtomicInteger(0)
        enum class ExampleGenerationStatus {
            CREATED, EXISTED, ERROR
        }

        fun resetExampleFileNameCounter() {
            exampleFileNamePostFixCounter.set(0)
        }

        class ExampleGenerationResult private constructor (val path: String?, val status: ExampleGenerationStatus) {
            constructor(path: String, created: Boolean) : this(path, if(created) ExampleGenerationStatus.CREATED else ExampleGenerationStatus.EXISTED)
            constructor(): this(null, ExampleGenerationStatus.ERROR)
        }

        fun testExample(test: ContractTest, testBaseUrl: String): Pair<Result, String> {
            val testResult = test.runTest(testBaseUrl, timeoutInMilliseconds = DEFAULT_TIMEOUT_IN_MILLISECONDS)
            val testLog = TestInteractionsLog.testHttpLogMessages.lastOrNull { it.scenario == testResult.first.scenario }

            return testResult.first to (testLog?.combineLog() ?: "No Test Logs Found")
        }

        fun generate(contractFile: File, scenarioFilter: ScenarioFilter, extensive: Boolean): List<String> {
            try {
                val feature: Feature = parseContractFileToFeature(contractFile).let { feature ->
                    val filteredScenarios = if (!extensive) {
                        feature.scenarios.filter {
                            it.status.toString().startsWith("2")
                        }
                    } else {
                        feature.scenarios
                    }

                    scenarioFilter.filter(feature.copy(scenarios = filteredScenarios.map {
                        it.copy(examples = emptyList())
                    })).copy(stubsFromExamples = emptyMap())
                }

                val examplesDir =
                    getExamplesDirPath(contractFile)

                examplesDir.mkdirs()

                if (feature.scenarios.isEmpty()) {
                    logger.log("All examples were filtered out by the filter expression")
                    return emptyList()
                }

                return feature.scenarios.flatMap { scenario ->
                    try {
                        val examples = getExistingExampleFiles(scenario, examplesDir.getExamplesFromDir())
                            .map { ExamplePathInfo(it.first.absolutePath, false) }
                            .ifEmpty { listOf(generateExampleFile(contractFile, feature, scenario)) }

                        examples.forEach {
                            val loggablePath =
                                File(it.path).canonicalFile.relativeTo(contractFile.canonicalFile.parentFile).path

                            val trimmedScenarioDescription = scenario.testDescription().trim()

                            if (!it.created) {
                                println("Example exists for $trimmedScenarioDescription: $loggablePath")
                            } else {
                                println("Created example for $trimmedScenarioDescription: $loggablePath")
                            }
                        }

                        examples.map { ExampleGenerationResult(it.path, it.created) }
                    } catch(e: Throwable) {
                        logger.log(e, "Exception generating example for ${scenario.testDescription()}")
                        emptyList()
                    }
                }.also { exampleFiles ->
                    val resultCounts = exampleFiles.groupBy { it.status }.mapValues { it.value.size }
                    val createdFileCount = resultCounts[ExampleGenerationStatus.CREATED] ?: 0
                    val errorCount = resultCounts[ExampleGenerationStatus.ERROR] ?: 0
                    val existingFileCount = resultCounts[ExampleGenerationStatus.EXISTED] ?: 0

                    logger.log(System.lineSeparator() + "NOTE: All examples may be found in ${getExamplesDirPath(contractFile).canonicalFile}" + System.lineSeparator())

                    val errorsClause = if(errorCount > 0) ", $errorCount examples could not be generated due to errors" else ""

                    logger.log("=============== Example Generation Summary ===============")
                    logger.log("$createdFileCount example(s) created, $existingFileCount examples already existed$errorsClause")
                    logger.log("==========================================================")
                }.mapNotNull { it.path }
            } catch (e: StackOverflowError) {
                logger.log("Got a stack overflow error. You probably have a recursive data structure definition in the contract.")
                throw e
            }
        }

        fun generate(
            contractFile: File,
            method: String,
            path: String,
            responseStatusCode: Int,
            contentType: String? = null,
        ): List<ExamplePathInfo> {
            val feature = parseContractFileToFeature(contractFile)
            val scenario: Scenario? = feature.scenarios.firstOrNull {
                it.method == method && it.status == responseStatusCode && it.path == path
                        && (contentType == null || it.httpRequestPattern.headersPattern.contentType == contentType)
            }
            if(scenario == null) return emptyList()

            val examplesDir = getExamplesDirPath(contractFile)
            val examples = examplesDir.getExamplesFromDir()

            return getExistingExampleFiles(scenario, examples).map {
                ExamplePathInfo(it.first.absolutePath, false)
            }.plus(generateExampleFiles(contractFile, feature, scenario))
        }

        data class ExamplePathInfo(val path: String, val created: Boolean)

        private fun generateExampleFile(
            contractFile: File,
            feature: Feature,
            scenario: Scenario,
        ): ExamplePathInfo {
            val examplesDir = getExamplesDirPath(contractFile)
            if(!examplesDir.exists()) examplesDir.mkdirs()

            val request = scenario.generateHttpRequest()
            val response = feature.lookupResponse(scenario).cleanup()

            val scenarioStub = ScenarioStub(request, response)
            val stubJSON = scenarioStub.toJSON()
            val uniqueNameForApiOperation = uniqueNameForApiOperation(scenarioStub.request, "", scenarioStub.response.status)

            val file = examplesDir.resolve("${uniqueNameForApiOperation}_${exampleFileNamePostFixCounter.incrementAndGet()}.json")
            println("Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}")
            file.writeText(stubJSON.toStringLiteral())
            return ExamplePathInfo(file.absolutePath, true)
        }


        private fun generateExampleFiles(
            contractFile: File,
            feature: Feature,
            scenario: Scenario,
        ): List<ExamplePathInfo> {
            val examplesDir = getExamplesDirPath(contractFile)
            if(!examplesDir.exists()) examplesDir.mkdirs()

            val generatedRequestResponses = feature.generateRequestResponses(scenario).map {
                it.copy(response = it.response.cleanup())
            }

            return generatedRequestResponses.map { (request, response, kind) ->
                val scenarioStub = ScenarioStub(request, response)
                val stubJSON = scenarioStub.toJSON()
                val uniqueNameForApiOperation = uniqueNameForApiOperation(
                    scenarioStub.request,
                    "",
                    scenarioStub.response.status
                )  + if (kind.isNotEmpty()) "_$kind" else ""

                val file = examplesDir.resolve("${uniqueNameForApiOperation}_${exampleFileNamePostFixCounter.incrementAndGet()}.json")
                println("Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}")
                file.writeText(stubJSON.toStringLiteral())
                ExamplePathInfo(file.absolutePath, true)
            }
        }

        fun validateSingleExample(contractFile: File, exampleFile: File): Result {
            val feature = parseContractFileToFeature(contractFile)
            return validateSingleExample(feature, exampleFile)
        }

        fun validateSingleExample(feature: Feature, exampleFile: File): Result {
            val scenarioStub = ScenarioStub.readFromFile(exampleFile)

            return try {
                validateExample(feature, scenarioStub)
                Result.Success()
            } catch(e: NoMatchingScenario) {
                e.results.toResultIfAny()
            }
        }

        fun validateMultipleExamples(contractFile: File, examples: Map<String, List<ScenarioStub>> = emptyMap(), scenarioFilter: ScenarioFilter = ScenarioFilter()): Map<String, Result> {
            val feature = parseContractFileToFeature(contractFile)
            return validateMultipleExamples(feature, examples, false, scenarioFilter)
        }

        fun validateMultipleExamples(feature: Feature, examples: Map<String, List<ScenarioStub>> = emptyMap(), inline: Boolean = false, scenarioFilter: ScenarioFilter = ScenarioFilter()): Map<String, Result> {
            val updatedFeature = scenarioFilter.filter(feature)

            val results = examples.mapValues { (name, exampleList) ->
                logger.log("Validating $name")

                exampleList.mapNotNull { example ->
                    try {
                        validateExample(updatedFeature, example)
                        Result.Success()
                    } catch (e: NoMatchingScenario) {
                        if (inline && !e.results.withoutFluff().hasResults())
                            null
                        else
                            e.results.toResultIfAny()
                    }
                }.let {
                    Result.fromResults(it)
                }
            }

            return results
        }

        private fun getCleanedUpFailure(
            failureResults: Results,
            noMatchingScenario: NoMatchingScenario?
        ): Results {
            return failureResults.toResultIfAny().let {
                if (it.reportString().isBlank())
                    Results(listOf(Result.Failure(noMatchingScenario?.message ?: "", failureReason = FailureReason.ScenarioMismatch)))
                else
                    failureResults
            }
        }

        private fun validateExample(
            feature: Feature,
            scenarioStub: ScenarioStub
        ) {
            val result: Pair<Pair<Result.Success, List<HttpStubData>>?, NoMatchingScenario?> =
                HttpStub.setExpectation(scenarioStub, feature, InteractiveExamplesMismatchMessages)
            val validationResult = result.first
            val noMatchingScenario = result.second

            if (validationResult == null) {
                val failures = noMatchingScenario?.results?.withoutFluff()?.results ?: emptyList()

                val failureResults = Results(failures).withoutFluff().let {
                    getCleanedUpFailure(it, noMatchingScenario)
                }
                throw NoMatchingScenario(
                    failureResults,
                    cachedMessage = failureResults.report(scenarioStub.request),
                    msg = failureResults.report(scenarioStub.request)
                )
            }
        }

        private fun HttpResponse.cleanup(): HttpResponse {
            return this.copy(headers = this.headers.minus(SPECMATIC_RESULT_HEADER))
        }

        fun getExistingExampleFiles(scenario: Scenario, examples: List<ExampleFromFile>): List<Pair<File, String>> {
            return examples.mapNotNull { example ->
                val response = example.response

                when (val matchResult = scenario.matchesMock(example.request, response)) {
                    is Result.Success -> example.file to ""
                    is Result.Failure -> {
                        val isFailureRelatedToScenario = matchResult.getFailureBreadCrumbs("").none { breadCrumb ->
                            breadCrumb.contains(PATH_BREAD_CRUMB)
                                    || breadCrumb.contains(METHOD_BREAD_CRUMB)
                                    || breadCrumb.contains("REQUEST.HEADERS.Content-Type")
                                    || breadCrumb.contains("STATUS")
                        }
                        if (isFailureRelatedToScenario) example.file to matchResult.reportString() else null
                    }
                }
            }
        }

        private fun getExamplesDirPath(contractFile: File): File {
            return contractFile.canonicalFile
                .parentFile
                .resolve("""${contractFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX""")
        }

        fun File.getExamplesFromDir(): List<ExampleFromFile> {
            return this.listFiles()?.map { ExampleFromFile(it) } ?: emptyList()
        }

    }
}

object InteractiveExamplesMismatchMessages : MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Specification expected $expected but example contained $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.capitalizeFirstChar()} $keyName in the example is not in the specification"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "${keyLabel.capitalizeFirstChar()} $keyName in the specification is missing from the example"
    }
}

data class ExamplePageRequest(
    val contractFile: String,
    val hostPort: String
)

data class ValidateExampleRequest(
    val exampleFile: String
)

data class ValidateExampleResponse(
    val absPath: String,
    val error: String? = null
)

enum class ValidateExampleVerdict {
    SUCCESS,
    FAILURE
}

data class ValidateExampleResponseV2(
    val verdict: ValidateExampleVerdict,
    val message: String,
    val exampleFilePath: String
)

data class GenerateExampleRequest(
    val method: String,
    val path: String,
    val responseStatusCode: Int,
    val contentType: String? = null
)

data class GenerateExample(
    val exampleFilePath: String,
    val created: Boolean
)

data class GenerateExampleResponse (
    val examples: List<GenerateExample>
) {
    companion object {
        fun from(infos: List<ExamplesInteractiveServer.Companion.ExamplePathInfo>): GenerateExampleResponse {
            return GenerateExampleResponse(infos.map { GenerateExample(it.path, it.created) })
        }
    }
}

data class ExampleTestRequest(
    val exampleFile: String
)

data class ExampleTestResponse(
    val result: TestResult,
    val details: String,
    val testLog: String
) {
    constructor(result: Result, testLog: String, exampleFile: File): this (
        result = result.testResult(),
        details = resultToDetails(result, exampleFile),
        testLog = when(result.isSuccess()) {
            true -> testLog
            false -> "${result.reportString()}\n\n$testLog"
        }
    )

    companion object {
        fun resultToDetails(result: Result, exampleFile: File): String {
            val postFix = when(result.testResult()) {
                TestResult.Success -> "has SUCCEEDED"
                TestResult.Error -> "has ERROR"
                else -> "has FAILED"
            }

            return "Example test for ${exampleFile.nameWithoutExtension} $postFix"
        }
    }
}

fun loadExternalExamples(contractFile: File): Pair<File, Map<String, List<ScenarioStub>>> {
    val examplesDir =
        contractFile.absoluteFile.parentFile.resolve(contractFile.nameWithoutExtension + "_examples")
    if (!examplesDir.isDirectory) {
        logger.log("$examplesDir does not exist, did not find any files to validate")
        exitProcess(1)
    }

    return examplesDir to examplesDir.walk().mapNotNull {
        if (it.isFile)
            Pair(it.path, it)
        else
            null
    }.toMap().mapValues {
        listOf(ScenarioStub.readFromFile(it.value))
    }
}
