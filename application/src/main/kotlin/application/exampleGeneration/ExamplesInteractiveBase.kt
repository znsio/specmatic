package application.exampleGeneration

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.specmatic.core.Result
import io.specmatic.core.TestResult
import io.specmatic.core.log.consoleDebug
import io.specmatic.core.log.consoleLog
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.test.reports.coverage.html.HtmlTemplateConfiguration
import picocli.CommandLine.Option
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.lang.Thread.sleep

abstract class ExamplesInteractiveBase<Feature, Scenario> (
    override val featureStrategy: ExamplesFeatureStrategy<Feature, Scenario>,
    private val generationStrategy: ExamplesGenerationStrategy<Feature, Scenario>,
    private val validationStrategy: ExamplesValidationStrategy<Feature, Scenario>
): ExamplesBase<Feature, Scenario>(featureStrategy) {
    @Option(names = ["--testBaseURL"], description = ["BaseURL of the the system to test"], required = false)
    protected var sutBaseUrl: String? = null

    @Option(names = ["--contract-file"], description = ["Contract file path"], required = false)
    override var contractFile: File? = null

    @Option(names = ["--dictionary"], description = ["Path to external dictionary file (default: contract_file_name_dictionary.json or dictionary.json)"])
    protected var dictFile: File? = null

    protected abstract val htmlTableColumns: List<HtmlTableColumn>
    private var cachedContractFileFromRequest: File? = null

    override fun execute(contract: File?): Int {
        try {
            if (contract == null) {
                consoleLog("Contract file not provided, Please provide one via HTTP request")
            }

            updateDictionaryFile(dictFile)
            val server = InteractiveServer(contract, sutBaseUrl, "0.0.0.0", 9001)
            addShutdownHook(server)
            consoleLog("Examples Interactive server is running on http://0.0.0.0:9001/_specmatic/examples. Ctrl + C to stop.")
            while (true) sleep(10000)
        } catch (e: Throwable) {
            consoleLog("Example Interactive server failed with error: ${e.message}")
            consoleDebug(e)
            return 1
        }
    }

    // HOOKS
    abstract fun createTableRows(scenarioExamplePair: List<Pair<Scenario, ExampleValidationResult?>>): List<TableRow>

    abstract suspend fun getScenarioFromRequestOrNull(call: ApplicationCall, feature: Feature): Scenario?

    abstract fun testExternalExample(feature: Feature, exampleFile: File, testBaseUrl: String): Pair<Result, String>

    // HELPER METHODS
    private suspend fun generateExample(call: ApplicationCall, contractFile: File): ExampleGenerationResult {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val examplesDir = getExamplesDirectory(contractFile)
        val exampleFiles = getExternalExampleFiles(examplesDir)

        return getScenarioFromRequestOrNull(call, feature)?.let {
            generationStrategy.generateOrGetExistingExample(
                ExamplesGenerationStrategy.GenerateOrGetExistingExampleArgs(
                    feature, it,
                    featureStrategy.getScenarioDescription(it),
                    exampleFiles, examplesDir,
                    featureStrategy.exampleFileExtensions
                )
            )
        } ?: throw IllegalArgumentException("No matching scenario found for request")
    }

    private fun validateExample(contractFile: File, exampleFile: File): ExampleValidationResult {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val result = validationStrategy.validateExternalExample(feature, exampleFile)
        return ExampleValidationResult(exampleFile.absolutePath, result.second, ExampleType.EXTERNAL)
    }

    private fun testExample(contractFile: File, exampleFile: File, sutBaseUrl: String): ExampleTestResult {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val result = testExternalExample(feature, exampleFile, sutBaseUrl)
        return ExampleTestResult(result.first, result.second, exampleFile)
    }

    private fun getContractFileOrNull(contractFile: File?, request: ExamplePageRequest? = null): File? {
        return contractFile?.takeIf { it.exists() }?.also { contract ->
            consoleDebug("Using Contract file ${contract.path} provided via command line")
        } ?: request?.contractFile?.takeIf { it.exists() }?.also { contract ->
            consoleDebug("Using Contract file ${contract.path} provided via HTTP request")
        } ?: cachedContractFileFromRequest?.takeIf { it.exists() }?.also { contract ->
            consoleDebug("Using Contract file ${contract.path} provided via cached HTTP request")
        }
    }

    private fun validateRows(tableRows: List<TableRow>): List<TableRow> {
        tableRows.forEach { row ->
            require(row.columns.size == htmlTableColumns.size) {
                consoleDebug("Invalid Row: $row")
                throw IllegalArgumentException("Incorrect number of columns in table row. Expected: ${htmlTableColumns.size}, Actual: ${row.columns.size}")
            }

            row.columns.forEachIndexed { index, it ->
                require(it.columnName == htmlTableColumns[index].name) {
                    consoleDebug("Invalid Column Row: $row")
                    throw IllegalArgumentException("Incorrect column name in table row. Expected: ${htmlTableColumns[index].name}, Actual: ${it.columnName}")
                }
            }
        }

        return tableRows
    }

    private fun getTableRows(contractFile: File): List<TableRow> {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val scenarios = getFilteredScenarios(feature)
        val examplesDir = getExamplesDirectory(contractFile)
        val examples = getExternalExampleFiles(examplesDir)

        val scenarioExamplePair = scenarios.map {
            it to generationStrategy.getExistingExampleOrNull(it, examples)?.let { exRes ->
                ExampleValidationResult(exRes.first, exRes.second)
            }
        }
        val tableRows = createTableRows(scenarioExamplePair)

        return validateRows(tableRows)
    }

    // INTERACTIVE SERVER
    inner class InteractiveServer(private var contract: File?, sutBaseUrl: String?, private val serverHost: String, private val serverPort: Int) : Closeable {
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

                install(StatusPages) {
                    exception<Throwable> { call, cause ->
                        consoleDebug(cause)
                        call.respondWithError(cause)
                    }
                }

                configureHealthCheckModule()

                routing {
                    get("/_specmatic/examples") {
                        getValidatedContractFileOrNull()?.let { contract ->
                            val hostPort = getServerHostAndPort()
                            val htmlContent = getHtmlContent(contract, hostPort)
                            call.respondText(htmlContent, contentType = ContentType.Text.Html)
                        }
                    }

                    post("/_specmatic/examples") {
                        val request = call.receive<ExamplePageRequest>()
                        getValidatedContractFileOrNull(request)?.let { contract ->
                            val hostPort = getServerHostAndPort()
                            val htmlContent = getHtmlContent(contract, hostPort)
                            call.respondText(htmlContent, contentType = ContentType.Text.Html)
                        }
                    }

                    post("/_specmatic/examples/generate") {
                        getValidatedContractFileOrNull()?.let { contractFile ->
                            val result = generateExample(call, contractFile)
                            call.respond(HttpStatusCode.OK, GenerateExampleResponse(result))
                        }
                    }

                    post("/_specmatic/examples/validate") {
                        val request = call.receive<ExampleValidationRequest>()
                        getValidatedContractFileOrNull()?.let { contract ->
                            getValidatedExampleOrNull(request.exampleFile)?.let { example ->
                                val result = validateExample(contract, example)
                                call.respond(HttpStatusCode.OK, ExampleValidationResponse(result))
                            }
                        }
                    }

                    post("/_specmatic/examples/content") {
                        val request = call.receive<ExampleContentRequest>()
                        getValidatedExampleOrNull(request.exampleFile)?.let { example ->
                            call.respond(HttpStatusCode.OK, mapOf("content" to example.readText()))
                        }
                    }

                    post("/_specmatic/examples/test") {
                        if (sutBaseUrl.isNullOrBlank()) {
                            return@post call.respondWithError(HttpStatusCode.BadRequest, "No SUT URL provided")
                        }

                        val request = call.receive<ExampleTestRequest>()
                        getValidatedContractFileOrNull()?.let { contract ->
                            getValidatedExampleOrNull(request.exampleFile)?.let { example ->
                                val result = testExample(contract, example, sutBaseUrl)
                                call.respond(HttpStatusCode.OK, ExampleTestResponse(result))
                            }
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

        private fun getServerHostAndPort(request: ExamplePageRequest? = null): String {
            return request?.hostPort.takeIf { !it.isNullOrEmpty() } ?: "localhost:$serverPort"
        }

        private fun getHtmlContent(contractFile: File, hostPort: String): String {
            val tableRows = getTableRows(contractFile)
            val htmlContent = renderTemplate(contractFile, hostPort, tableRows)
            return htmlContent
        }

        private fun renderTemplate(contractFile: File, hostPort: String, tableRows: List<TableRow>): String {
            val variables = mapOf(
                "tableColumns" to htmlTableColumns,
                "tableRows" to tableRows,
                "contractFileName" to contractFile.name,
                "contractFilePath" to contractFile.absolutePath,
                "hostPort" to hostPort,
                "hasExamples" to tableRows.any { it.exampleFilePath != null },
                "validationDetails" to tableRows.mapIndexed { index, row ->
                    (index + 1) to row.exampleMismatchReason
                }.toMap(),
                "isTestMode" to (sutBaseUrl != null)
            )

            return HtmlTemplateConfiguration.process(
                templateName = "example/index.html",
                variables = variables
            )
        }

        private suspend fun ApplicationCall.respondWithError(httpStatusCode: HttpStatusCode, errorMessage: String) {
            this.respond(httpStatusCode, mapOf("error" to errorMessage))
        }

        private suspend fun ApplicationCall.respondWithError(throwable: Throwable, errorMessage: String? = throwable.message) {
            val statusCode = when (throwable) {
                is IllegalArgumentException, is FileNotFoundException -> HttpStatusCode.BadRequest
                else -> HttpStatusCode.InternalServerError
            }
            this.respondWithError(statusCode, errorMessage ?: throwable.message ?: "Something went wrong")
        }

        private suspend fun PipelineContext<Unit, ApplicationCall>.getValidatedContractFileOrNull(request: ExamplePageRequest? = null): File? {
            val contractFile = getContractFileOrNull(contract, request) ?: run {
                val errorMessage = "No Contract File Found - Please provide a contract file in the command line or in the HTTP request."
                consoleLog(errorMessage)
                call.respondWithError(HttpStatusCode.BadRequest, errorMessage)
                return null
            }

            return getValidatedContractFileOrNull(contractFile)?.let { contract ->
                updateDictionaryFile(dictFile, contract)
                return contract
            } ?: run {
                val errorMessage = "Invalid Contract file ${contractFile.path} - File extension must be one of ${featureStrategy.contractFileExtensions.joinToString()}"
                call.respondWithError(HttpStatusCode.BadRequest, errorMessage)
                return null
            }
        }

        private suspend fun PipelineContext<Unit, ApplicationCall>.getValidatedExampleOrNull(exampleFile: File): File? {
            if (!exampleFile.exists()) {
                val errorMessage = "Example file does not exist: ${exampleFile.absolutePath}"
                call.respondWithError(HttpStatusCode.BadRequest, errorMessage)
                return null
            }

            return getValidatedExampleFileOrNull(exampleFile) ?: run {
                val errorMessage = "Invalid Example file ${exampleFile.path} - File extension must be one of ${featureStrategy.exampleFileExtensions.joinToString()}"
                call.respondWithError(HttpStatusCode.BadRequest, errorMessage)
                return null
            }
        }
    }

    private fun addShutdownHook(server: InteractiveServer) {
        Runtime.getRuntime().addShutdownHook(Thread {
            consoleLog("Shutting down examples interactive server...")
            try {
                server.close()
                consoleLog("Server shutdown completed successfully.")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                consoleLog("Server shutdown interrupted.")
            } catch (e: Throwable) {
                consoleLog("Server shutdown failed with error: ${e.message}")
                consoleDebug(e)
            }
        })
    }

    data class ExampleTestResult(val result: TestResult, val testLog: String, val exampleFile: File) {
        constructor(result: Result, testLog: String, exampleFile: File): this(
            result.testResult(),
            result.takeIf { !it.isSuccess() }?.let {
                "${it.reportString()}\n\n$testLog"
            } ?: testLog,
            exampleFile
        )
    }
}

data class ExamplePageRequest (
    val contractFile: File,
    val hostPort: String?
)

data class GenerateExampleResponse (
    val exampleFilePath: String,
    val status: String
) {
    constructor(result: ExampleGenerationResult): this(
        exampleFilePath = result.exampleFile?.absolutePath ?: throw Exception("Failed to generate example file"),
        status = result.status.toString()
    )
}

data class ExampleValidationRequest (
    val exampleFile: File
)

data class ExampleValidationResponse (
    val exampleFilePath: String,
    val error: String? = null
) {
    constructor(result: ExampleValidationResult): this(
        exampleFilePath = result.exampleName, error = result.result.reportString().takeIf { it.isNotBlank() }
    )
}

data class ExampleContentRequest (
    val exampleFile: File
)

data class ExampleTestRequest (
    val exampleFile: File
)

data class ExampleTestResponse (
    val result: TestResult,
    val details: String,
    val testLog: String
) {
    constructor(result: ExamplesInteractiveBase.ExampleTestResult): this (
        result = result.result,
        details = resultToDetails(result.result, result.exampleFile),
        testLog = result.testLog.trim('-', ' ', '\n', '\r')
    )

    companion object {
        fun resultToDetails(result: TestResult, exampleFile: File): String {
            val postFix = when(result) {
                TestResult.Success -> "has SUCCEEDED"
                TestResult.Error -> "has ERROR"
                else -> "has FAILED"
            }

            return "Example test for ${exampleFile.nameWithoutExtension} $postFix"
        }
    }
}

data class HtmlTableColumn (
    val name: String,
    val colSpan: Int
)

data class TableRowGroup (
    val columnName: String,
    val value: String,
    val rowSpan: Int = 1,
    val showRow: Boolean = true,
    val rawValue: String = value,
    val extraInfo: String? = null
)

data class TableRow (
    val columns: List<TableRowGroup>,
    val exampleFilePath: String? = null,
    val exampleFileName: String? = null,
    val exampleMismatchReason: String? = null
)
