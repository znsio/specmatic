package io.specmatic.core.examples.server

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.specmatic.core.DEFAULT_TIMEOUT_IN_MILLISECONDS
import io.specmatic.core.Feature
import io.specmatic.core.Result
import io.specmatic.core.SPECMATIC_STUB_DICTIONARY
import io.specmatic.core.examples.module.ExampleFixModule
import io.specmatic.core.examples.module.ExampleValidationModule
import io.specmatic.core.examples.server.ExamplesView.Companion.toTableRows
import io.specmatic.core.examples.server.SchemaExamplesView.Companion.schemaExamplesToTableRows
import io.specmatic.core.filters.ScenarioMetadataFilter
import io.specmatic.core.filters.ScenarioMetadataFilter.Companion.filterUsing
import io.specmatic.core.log.logger
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.test.ContractTest
import io.specmatic.test.TestInteractionsLog
import io.specmatic.test.TestInteractionsLog.combineLog
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import kotlin.system.exitProcess

class ExamplesInteractiveServer(
    private val serverHost: String,
    private val serverPort: Int,
    private val testBaseUrl: String?,
    private val inputContractFile: File? = null,
    private val filterName: String,
    private val filterNotName: String,
    private val filter: String,
    externalDictionaryFile: File? = null
) : Closeable {
    private var contractFileFromRequest: File? = null

    private val exampleModule = ExampleModule()
    private val exampleValidationModule = ExampleValidationModule()
    private val exampleFixModule = ExampleFixModule(exampleValidationModule)

    init {
        if(externalDictionaryFile != null) System.setProperty(SPECMATIC_STUB_DICTIONARY, externalDictionaryFile.path)
    }

    private fun getContractFile(): File {
        if(inputContractFile != null && inputContractFile.exists()) return inputContractFile
        if(contractFileFromRequest != null && contractFileFromRequest!!.exists()) return contractFileFromRequest!!
        throw ContractException("Invalid contract file provided to the examples interactive server")
    }

    private fun getServerHostPort(request: ExamplePageRequest? = null) : String {
        return (request?.hostPort ?: "http://localhost:$serverPort").trimEnd('/')
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
                staticResources("/_specmatic/examples/assets", "static")
                get("/_specmatic/examples") {
                    val contractFile = getContractFileOrBadRequest(call) ?: return@get
                    try {
                        respondWithExamplePageHtmlContent(contractFile, getServerHostPort(), call)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, exceptionCauseMessage(e))
                    }
                }

                post("/_specmatic/examples") {
                    val request = call.receive<ExamplePageRequest>()
                    contractFileFromRequest = File(request.contractFile)
                    val contractFile = getContractFileOrBadRequest(call) ?: return@post
                    try {
                        respondWithExamplePageHtmlContent(contractFile,getServerHostPort(request), call)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, exceptionCauseMessage(e))
                    }
                }

                post("/_specmatic/examples/update") {
                    val request = call.receive<SaveExampleRequest>()
                    try {
                        val file = File(request.exampleFile)
                        if (!file.exists()) {
                            throw FileNotFoundException()
                        }
                        file.writeText(request.exampleContent)
                        call.respond(HttpStatusCode.OK, "File and content updated successfully!")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, exceptionCauseMessage(e))
                    }
                }

                post("/_specmatic/examples/validate") {
                    val request = call.receive<ValidateExampleRequest>()
                    try {
                        val contractFile = getContractFile()
                        val validationResultResponse = try {
                            val result = exampleValidationModule.validateExample(contractFile, File(request.exampleFile))
                            if (result is Result.Failure) {
                                ValidateExampleResponse(
                                    absPath = request.exampleFile, errorMessage = result.reportString(),
                                    errorList = ExampleValidationDetails(result.toMatchFailureDetailList()).jsonPathToErrorDescriptionMapping(),
                                    isPartialFailure = result.isPartialFailure()
                                )
                            } else {
                                ValidateExampleResponse(request.exampleFile)
                            }
                        } catch (e: FileNotFoundException) {
                            ValidateExampleResponse(request.exampleFile, e.message ?: "File not found")
                        } catch (e: ContractException) {
                            ValidateExampleResponse(request.exampleFile, exceptionCauseMessage(e))
                        } catch (e: Exception) {
                            ValidateExampleResponse(request.exampleFile, e.message ?: "An unexpected error occurred")
                        }
                        call.respond(HttpStatusCode.OK, validationResultResponse)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("errorMessage" to exceptionCauseMessage(e))
                        )
                    }
                }

                post("/_specmatic/examples/fix") {
                    val request = call.receive<FixExampleRequest>()
                    try {
                        val contractFile = getContractFile()
                        val fixExamplesResponse = try {
                            exampleFixModule.fixExample(contractFile, request)
                        } catch (e: FileNotFoundException) {
                            FixExampleResponse(request.exampleFile, e.message ?: "File not found")
                        } catch (e: ContractException) {
                            FixExampleResponse(request.exampleFile, exceptionCauseMessage(e))
                        } catch (e: Exception) {
                            FixExampleResponse(request.exampleFile, e.message ?: "An unexpected error occurred")
                        }
                        call.respond(HttpStatusCode.OK, fixExamplesResponse)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("errorMessage" to exceptionCauseMessage(e))
                        )
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
                        val feature = parseContractFileToFeature(getContractFile())

                        val contractTest = feature.createContractTestFromExampleFile(request.exampleFile).value

                        val (result, testLog) = testExample(contractTest, testBaseUrl)

                        call.respond(HttpStatusCode.OK, ExampleTestResponse(result, testLog, exampleFile = File(request.exampleFile)))
                    } catch (e: Throwable) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to exceptionCauseMessage(e)))
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
        val html = getExamplePageHtmlContent(contractFile, hostPort)
        call.respondText(html, contentType = ContentType.Text.Html)
    }

    private fun getExamplePageHtmlContent(contractFile: File, hostPort: String): String {
        val feature = ScenarioFilter(
            filterName,
            filterNotName,
            filter
        ).filter(parseContractFileToFeature(contractFile))

        val examplesDir = exampleModule.getExamplesDirPath(contractFile)
        val endpoints = ExamplesView.getEndpoints(feature, examplesDir)
        val schemaExamplesPairs = exampleModule.getSchemaExamplesWithValidation(feature, examplesDir)

        val exampleTableRows = endpoints.toTableRows()
        val schemaTableRows = schemaExamplesToTableRows(feature, schemaExamplesPairs)

        // NOTE: Keep schemaTableRows before exampleTableRows
        val tableRows = schemaTableRows.plus(exampleTableRows)

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

    private fun List<TableRow>.transform(): Map<String, Map<String, Map<String, Any?>>> {
        return this.groupBy { it.uniqueKey }.mapValues { (_, keyGroup) ->
            keyGroup.associateBy(
                { it.example ?: "null" },
                {
                    mapOf(
                        "errorList" to ExampleValidationDetails(it.failureDetails).jsonPathToErrorDescriptionMapping(),
                        "errorMessage" to it.exampleMismatchReason
                    )
                }
            )
        }
    }

    private fun testExample(test: ContractTest, testBaseUrl: String): Pair<Result, String> {
        val testResult = test.runTest(testBaseUrl, timeoutInMilliseconds = DEFAULT_TIMEOUT_IN_MILLISECONDS)
        val testLog = TestInteractionsLog.testHttpLogMessages.lastOrNull { it.scenario == testResult.first.scenario }

        return testResult.first to (testLog?.combineLog() ?: "No Test Logs Found")
    }
}

class ScenarioFilter(filterName: String = "", filterNotName: String = "", filterClauses: String = "") {
    private val filter = filterClauses

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

        val scenarioFilter = ScenarioMetadataFilter.from(filter)

        val filteredScenarios = filterUsing(scenariosFilteredByOlderSyntax.asSequence(), scenarioFilter) {
            it.toScenarioMetadata()
        }.toList()


        return feature.copy(scenarios = filteredScenarios)
    }
}

fun loadExternalExamples(
    examplesDir: File
): Pair<File, List<File>> {
    if (!examplesDir.isDirectory) {
        logger.log("$examplesDir does not exist, did not find any files to validate")
        exitProcess(1)
    }

    return examplesDir to examplesDir.walk().mapNotNull {
        it.takeIf { it.isFile && it.extension == "json" }
    }.toList()
}

fun defaultExternalExampleDirFrom(contractFile: File): File {
    return contractFile.absoluteFile.parentFile.resolve(contractFile.nameWithoutExtension + "_examples")
}
