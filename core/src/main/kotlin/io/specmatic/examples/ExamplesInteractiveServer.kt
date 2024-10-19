package io.specmatic.examples

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
import io.specmatic.core.log.consoleDebug
import io.specmatic.core.log.consoleLog
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.templates.HtmlTemplateConfiguration
import java.io.File
import java.io.FileNotFoundException
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

class ExamplesInteractiveServer(provider: InteractiveServerProvider) : InteractiveServerProvider by provider {
    private var cachedContractFile: File? = null

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
                getHtmlPageRoute()
                generateExampleRoute()
                validateExampleRoute()
                getExampleContentRoute()
                testExampleRoute()
            }
        }

        connector {
            this.host = serverHost
            this.port = serverPort
        }
    }

    private val server: ApplicationEngine = embeddedServer(Netty, environment) {
        requestQueueLimit = 1000
        callGroupSize = 5
        connectionGroupSize = 20
        workerGroupSize = 20
    }

    fun start() {
        server.start()
    }

    fun close() {
        server.stop(0, 0)
    }

    private fun getServerHostAndPort(request: ExamplePageRequest? = null): String {
        return request?.hostPort ?: "http://localhost:$serverPort"
    }

    private fun getContractFileOrNull(request: ExamplePageRequest? = null): Result<File> {
        val contractFile = contractFile?.also {
            logContractFileUsage("command line", it)
        } ?: request?.contractFile?.takeIf { it.exists() }?.also {
            logContractFileUsage("HTTP request", it)
        } ?: cachedContractFile?.takeIf { it.exists() }?.also {
            logContractFileUsage("cached HTTP request", it)
        }

        return contractFile?.let {
            success(it)
        } ?: run {
            val errorMessage = "Contract file not provided or does not exist, Please provide one via HTTP request or command line"
            consoleLog(errorMessage)
            failure(IllegalArgumentException(errorMessage))
        }
    }

    private fun logContractFileUsage(source: String, contract: File) {
        consoleDebug("Using Contract file ${contract.path} provided via $source")
    }

    private fun getHtmlContent(contractFile: File, hostPort: String): String {
        val tableRows = validateRows(getTableRows(contractFile))
        return renderTemplate(contractFile, hostPort, tableRows)
    }

    private fun validateRows(tableRows: List<ExampleTableRow>): List<ExampleTableRow> {
        tableRows.forEach { row ->
            require(row.columns.size == exampleTableColumns.size) {
                consoleDebug("Invalid Row: $row")
                throw IllegalArgumentException("Incorrect number of columns in table row. Expected: ${exampleTableColumns.size}, Actual: ${row.columns.size}")
            }

            row.columns.forEachIndexed { index, it ->
                require(it.columnName == exampleTableColumns[index].name) {
                    consoleDebug("Invalid Column Row: $row")
                    throw IllegalArgumentException("Incorrect column name in table row. Expected: ${exampleTableColumns[index].name}, Actual: ${it.columnName}")
                }
            }
        }

        return tableRows
    }

    private fun renderTemplate(contractFile: File, hostPort: String, tableRows: List<ExampleTableRow>): String {
        val variables = mapOf(
            "tableColumns" to exampleTableColumns,
            "tableRows" to tableRows,
            "contractFileName" to contractFile.name,
            "contractFilePath" to contractFile.absolutePath,
            "hostPort" to hostPort,
            "hasExamples" to tableRows.any { it.isGenerated },
            "exampleDetails" to tableRows.transform(),
            "isTestMode" to (sutBaseUrl != null)
        )

        return HtmlTemplateConfiguration.process("example/index.html", variables)
    }

    private suspend fun ApplicationCall.respondWithError(httpStatusCode: HttpStatusCode, errorMessage: String) {
        respond(httpStatusCode, mapOf("error" to errorMessage))
    }

    private suspend fun ApplicationCall.respondWithError(throwable: Throwable, errorMessage: String? = throwable.message) {
        val statusCode = when (throwable) {
            is IllegalArgumentException, is FileNotFoundException -> HttpStatusCode.BadRequest
            else -> HttpStatusCode.InternalServerError
        }
        respondWithError(statusCode, errorMessage ?: "Something went wrong")
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handleContractFile(request: ExamplePageRequest? = null, block: suspend (File) -> Unit) {
        getContractFileOrNull(request).fold(
            onSuccess = { contract ->
                ensureValidContractFile(contract).fold(
                    onSuccess = { block(it) },
                    onFailure = { call.respondWithError(HttpStatusCode.BadRequest, it.message.orEmpty()) }
                )
            },
            onFailure = { call.respondWithError(HttpStatusCode.BadRequest, it.message.orEmpty()) }
        )
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handleExampleFile(exampleFile: File, block: suspend (File) -> Unit) {
        ensureValidExampleFile(exampleFile).fold(
            onSuccess = { block(it) },
            onFailure = { call.respondWithError(HttpStatusCode.BadRequest, it.message.orEmpty()) }
        )
    }

    private fun Routing.getHtmlPageRoute() {
        get("/_specmatic/examples") {
            handleContractFile { contract ->
                val htmlContent = getHtmlContent(contract, getServerHostAndPort())
                call.respondText(htmlContent, contentType = ContentType.Text.Html)
            }
        }

        post("/_specmatic/examples") {
            val request = call.receive<ExamplePageRequest>()
            handleContractFile(request) { contract ->
                cachedContractFile = contract
                val htmlContent = getHtmlContent(contract, getServerHostAndPort(request))
                call.respondText(htmlContent, contentType = ContentType.Text.Html)
            }
        }
    }

    private fun Routing.generateExampleRoute() {
        post("/_specmatic/examples/generate") {
            handleContractFile { contractFile ->
                val result = generateExample(call, contractFile)
                call.respond(HttpStatusCode.OK, ExampleGenerationResponseList.from(result))
            }
        }
    }

    private fun Routing.validateExampleRoute() {
        post("/_specmatic/examples/validate") {
            val request = call.receive<ExampleValidationRequest>()
            handleContractFile { contract ->
                handleExampleFile(request.exampleFile) { example ->
                    val result = validateExample(contract, example)
                    call.respond(HttpStatusCode.OK, ExampleValidationResponse(result))
                }
            }
        }
    }

    private fun Routing.getExampleContentRoute() {
        post("/_specmatic/examples/content") {
            val request = call.receive<ExampleContentRequest>()
            handleExampleFile(request.exampleFile) { example ->
                call.respond(HttpStatusCode.OK, mapOf("content" to example.readText()))
            }
        }
    }

    private fun Routing.testExampleRoute() {
        post("/_specmatic/examples/test") {
            if (sutBaseUrl.isNullOrBlank()) {
                return@post call.respondWithError(HttpStatusCode.BadRequest, "No SUT URL provided")
            }

            val request = call.receive<ExampleTestRequest>()
            handleContractFile { contract ->
                handleExampleFile(request.exampleFile) { example ->
                    val result = testExample(contract, example, sutBaseUrl)
                    call.respond(HttpStatusCode.OK, ExampleTestResponse(result))
                }
            }
        }
    }

    private fun List<ExampleTableRow>.transform(): Map<String, Map<String, String?>> {
        return this.groupBy { it.uniqueKey }.mapValues { (_, keyGroup) ->
            keyGroup.associateBy({ it.exampleFilePath ?: "null" }, { it.exampleMismatchReason })
        }
    }
}