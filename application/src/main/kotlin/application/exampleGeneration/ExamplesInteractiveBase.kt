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
import io.specmatic.core.TestResult
import io.specmatic.core.log.logger
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.test.reports.coverage.html.HtmlTemplateConfiguration
import picocli.CommandLine.Option
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.lang.Thread.sleep
import java.util.concurrent.Callable

abstract class ExamplesInteractiveBase<Feature, Scenario>(val common: ExamplesCommon<Feature, Scenario>): Callable<Int> {
    @Option(names = ["--testBaseURL"], description = ["BaseURL of the SUT"], required = true)
    lateinit var serverHost: String

    @Option(names = ["--contract-file"], description = ["Contract file path"], required = false)
    var contractFile: File? = null

    @Option(names = ["--debug"], description = ["Debug logs"])
    var verbose = false

    @Option(names = ["--dictionary"], description = ["External Dictionary File Path"])
    var dictFile: File? = null

    @Option(names = ["--filter-name"], description = ["Use only APIs with this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NAME}")
    var filterName: String = ""

    @Option(names = ["--filter-not-name"], description = ["Use only APIs which do not have this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}")
    var filterNotName: String = ""

    abstract var extensive: Boolean
    abstract val htmlTableColumns: List<HtmlTableColumn>
    private var cachedContractFileFromRequest: File? = null

    override fun call(): Int {
        common.configureLogger(verbose)

        try {
            contractFile?.let { contract ->
                if (!contract.exists()) {
                    logger.log("Could not find Contract file ${contract.path}")
                    return 1
                }

                if (contract.extension !in common.contractFileExtensions) {
                    logger.log("Invalid Contract file ${contract.path} - File extension must be one of ${common.contractFileExtensions.joinToString()}")
                    return 1
                }

            } ?: logger.log("No contract file provided, Please provide a contract file in the HTTP request.")

            val server = InteractiveServer("0.0.0.0", 9001)
            addShutdownHook(server)
            logger.log("Examples Interactive server is running on http://0.0.0.0:9001/_specmatic/examples. Ctrl + C to stop.")
            while (true) sleep(10000)
        } catch (e: Throwable) {
            logger.log("Example Interactive server failed with error: ${e.message}")
            logger.debug(e)
            return 1
        }
    }

    // HOOKS
    abstract fun createTableRows(scenarios: List<Scenario>, exampleFiles: List<File>): List<TableRow>

    abstract suspend fun getScenarioFromRequestOrNull(call: ApplicationCall, feature: Feature): Scenario?

    abstract fun testExternalExample(feature: Feature, exampleFile: File, testBaseUrl: String): Pair<TestResult, String>

    // HELPER METHODS
    private suspend fun generateExample(call: ApplicationCall, contractFile: File): ExampleGenerationResult {
        val feature = common.contractFileToFeature(contractFile)
        val dictionary = common.loadExternalDictionary(dictFile, contractFile)
        val examplesDir = common.getExamplesDirectory(contractFile)
        val exampleFiles = common.getExternalExampleFiles(examplesDir)

        return getScenarioFromRequestOrNull(call, feature)?.let {
            common.generateOrGetExistingExample(feature, it, dictionary, exampleFiles, examplesDir)
        } ?: throw IllegalArgumentException("No matching scenario found for request")
    }

    private fun validateExample(contractFile: File, exampleFile: File): ExampleValidationResult {
        val feature = common.contractFileToFeature(contractFile)
        val result = common.validateExternalExample(feature, exampleFile)
        return ExampleValidationResult(exampleFile.absolutePath, result.second, ExampleType.EXTERNAL)
    }

    private fun testExample(contractFile: File, exampleFile: File): Pair<TestResult, String> {
        val feature = common.contractFileToFeature(contractFile)
        val result = testExternalExample(feature, exampleFile, serverHost)
        return Pair(result.first, result.second)
    }

    private fun getFilteredScenarios(feature: Feature): List<Scenario> {
        val scenarioFilter = ScenarioFilter(filterName, filterNotName, extensive)
        return common.getFilteredScenarios(feature, scenarioFilter)
    }

    private fun getContractFileOrNull(request: ExamplePageRequest? = null): File? {
        return contractFile?.takeIf { it.exists() }?.also { contract ->
            logger.debug("Using Contract file ${contract.path} provided via command line")
        } ?: request?.contractFile?.takeIf { it.exists() }?.also { contract ->
            logger.debug("Using Contract file ${contract.path} provided via HTTP request")
        } ?: cachedContractFileFromRequest?.takeIf { it.exists() }?.also { contract ->
            logger.debug("Using Contract file ${contract.path} provided via cached HTTP request")
        }
    }

    private fun validateRows(tableRows: List<TableRow>): List<TableRow> {
        tableRows.forEach { row ->
            require(row.columns.size == htmlTableColumns.size) {
                logger.debug("Invalid Row: $row")
                throw IllegalArgumentException("Incorrect number of columns in table row. Expected: ${htmlTableColumns.size}, Actual: ${row.columns.size}")
            }

            row.columns.forEachIndexed { index, it ->
                require(it.columnName == htmlTableColumns[index].name) {
                    logger.debug("Invalid Column Row: $row")
                    throw IllegalArgumentException("Incorrect column name in table row. Expected: ${htmlTableColumns[index].name}, Actual: ${it.columnName}")
                }
            }
        }

        return tableRows
    }

    private fun getTableRows(contractFile: File): List<TableRow> {
        val feature = common.contractFileToFeature(contractFile)
        val scenarios = getFilteredScenarios(feature)
        val examplesDir = common.getExamplesDirectory(contractFile)
        val examples = common.getExternalExampleFiles(examplesDir)
        val tableRows = createTableRows(scenarios, examples)

        return validateRows(tableRows)
    }

    // INTERACTIVE SERVER
    inner class InteractiveServer(private val serverHost: String, private val serverPort: Int) : Closeable {
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
                        logger.debug(cause)
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
                        val request = call.receive<ExampleTestRequest>()
                        getValidatedContractFileOrNull()?.let { contract ->
                            getValidatedExampleOrNull(request.exampleFile)?.let { example ->
                                val (result, testLog) = testExample(contract, example)
                                call.respond(HttpStatusCode.OK, ExampleTestResponse(result, testLog, example))
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
            return request?.hostPort.takeIf {
                !it.isNullOrEmpty()
            } ?: "localhost:$serverPort"
        }

        private fun getHtmlContent(contractFile: File, hostPort: String): String {
            val tableRows = getTableRows(contractFile)
            val htmlContent = renderTemplate(contractFile, hostPort, tableRows)
            return htmlContent
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
            val contractFile = getContractFileOrNull(request) ?: run {
                val errorMessage = "No Contract File Found - Please provide a contract file in the command line or in the HTTP request."
                logger.log(errorMessage)
                call.respondWithError(HttpStatusCode.BadRequest, errorMessage)
                return null
            }

            return contractFile.takeIf { it.extension in common.contractFileExtensions } ?: run {
                val errorMessage = "Invalid Contract file ${contractFile.path} - File extension must be one of ${common.contractFileExtensions.joinToString()}"
                logger.log(errorMessage)
                call.respondWithError(HttpStatusCode.BadRequest, errorMessage)
                return null
            }
        }

        private suspend fun PipelineContext<Unit, ApplicationCall>.getValidatedExampleOrNull(exampleFile: File): File? {
            return when {
                !exampleFile.exists() -> {
                    val errorMessage = "Could not find Example file ${exampleFile.path}"
                    logger.log(errorMessage)
                    call.respondWithError(HttpStatusCode.BadRequest, errorMessage)
                    return null
                }

                exampleFile.extension !in common.exampleFileExtensions -> {
                    val errorMessage = "Invalid Example file ${exampleFile.path} - File extension must be one of ${common.exampleFileExtensions.joinToString()}"
                    logger.log(errorMessage)
                    call.respondWithError(HttpStatusCode.BadRequest, errorMessage)
                    return null
                }

                else -> exampleFile
            }
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
                }.toMap()
            )

            return HtmlTemplateConfiguration.process(
                templateName = "example/index.html",
                variables = variables
            )
        }
    }

    private fun addShutdownHook(server: InteractiveServer) {
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.log("Shutting down examples interactive server...")
            try {
                server.close()
                logger.log("Server shutdown completed successfully.")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.log("Server shutdown interrupted.")
            } catch (e: Throwable) {
                logger.log("Server shutdown failed with error: ${e.message}")
                logger.debug(e)
            }
        })
    }
}

data class ExamplePageRequest (
    val contractFile: File,
    val hostPort: String?
)

data class GenerateExampleResponse(
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

data class ExampleValidationResponse(
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

data class ExampleTestResponse(
    val result: TestResult,
    val details: String,
    val testLog: String
) {
    constructor(result: TestResult, testLog: String, exampleFile: File): this (
        result = result,
        details = resultToDetails(result, exampleFile),
        testLog = testLog.trim('-', ' ', '\n', '\r')
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
