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
import io.specmatic.core.*
import io.specmatic.core.examples.server.ExamplesView.Companion.groupEndpoints
import io.specmatic.core.examples.server.ExamplesView.Companion.toTableRows
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.core.value.Value
import io.specmatic.mock.NoMatchingScenario
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.HttpStub
import io.specmatic.stub.HttpStubData
import io.specmatic.test.TestExecutor
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException

class ExamplesInteractiveServer(
    private val serverHost: String,
    private val serverPort: Int,
    private val inputContractFile: File? = null,
    private val filterName: String,
    private val filterNotName: String
) : Closeable {
    private var contractFileFromRequest: File? = null

    private fun getContractFile(): File {
        if(inputContractFile != null && inputContractFile.exists()) return inputContractFile
        if(contractFileFromRequest != null && contractFileFromRequest!!.exists()) return contractFileFromRequest!!
        throw ContractException("Invalid contract file provided to the examples interactive server")
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
                post("/_specmatic/examples") {
                    val request = call.receive<ExamplePageRequest>()
                    contractFileFromRequest = File(request.contractFile)
                    val contractFile = getContractFileOrBadRequest(call) ?: return@post
                    try {
                        respondWithExamplePageHtmlContent(getContractFile(), call)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
                    }
                }

                get("/_specmatic/examples") {
                    respondWithExamplePageHtmlContent(getContractFile(), call)
                }

                post("/_specmatic/examples/generate") {
                    val contractFile = getContractFile()
                    try {
                        val request = call.receive<GenerateExampleRequest>()
                        val generatedExamples = generate(
                            contractFile,
                            request.method,
                            request.path,
                            request.responseStatusCode,
                            request.contentType
                        )
                        call.respond(HttpStatusCode.OK, GenerateExampleResponse(generatedExamples))
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
                    }
                }

                post("/_specmatic/examples/validate") {
                    val request = call.receive<ValidateExampleRequest>()
                    try {
                        val contractFile = getContractFile()
                        val validationResults = request.exampleFiles.map {
                            try {
                                validate(contractFile, File(it))
                                ValidateExampleResponse(it, File(it).readText())
                            } catch(e: FileNotFoundException){
                                ValidateExampleResponse(it, "File not found", e.message)
                            } catch(e: NoMatchingScenario) {
                                ValidateExampleResponse(it, File(it).readText(), e.msg ?: "Something went wrong")
                            } catch(e: Exception) {
                                ValidateExampleResponse(it, File(it).readText(), e.message)
                            }
                        }
                        call.respond(HttpStatusCode.OK, validationResults)
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An unexpected error occurred: ${e.message}"))
                    }
                }

                post("/_specmatic/v2/examples/validate") {
                    val request = call.receive<ValidateExampleRequest>()
                    try {
                        val contractFile = getContractFile()
                        val validationResults = request.exampleFiles.map {
                            try {
                                validate(contractFile, File(it))
                                ValidateExampleResponseV2(
                                    ValidateExampleVerdict.SUCCESS,
                                    "The provided example is valid",
                                    it
                                )
                            } catch(e: NoMatchingScenario) {
                                ValidateExampleResponseV2(
                                    ValidateExampleVerdict.FAILURE,
                                    e.msg ?: "Something went wrong",
                                    it
                                )
                            } catch(e: Exception) {
                                ValidateExampleResponseV2(
                                    ValidateExampleVerdict.FAILURE,
                                    e.message ?: "An unexpected error occurred",
                                    it
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
                        call.respond(HttpStatusCode.BadRequest, "Invalid request. Missing required query param named 'fileName'")
                        return@get
                    }
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("content" to File(fileName).readText())
                    )
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

    private suspend fun respondWithExamplePageHtmlContent(contractFile: File, call: ApplicationCall) {
        try {
            val html = getExamplePageHtmlContent(contractFile)
            call.respondText(html, contentType = ContentType.Text.Html)
        } catch (e: Exception) {
            println(e)
            call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
        }
    }

    private fun getExamplePageHtmlContent(contractFile: File): String {
        val feature = filterScenarios(parseContractFileToFeature(contractFile))

        val endpoints = ExamplesView.getEndpoints(feature)
        return HtmlTemplateConfiguration.process(
            templateName = "examples/index.html",
            variables = mapOf(
                "tableRows" to endpoints.groupEndpoints().toTableRows(),
                "contractFile" to contractFile.name,
                "contractFilePath" to contractFile.absolutePath
            )
        )
    }

    private fun filterScenarios(feature: Feature): Feature {
        val filterNameTokens = if(filterName.isNotBlank()) {
            filterName.trim().split(",").map { it.trim() }
        } else emptyList()

        val filterNotNameTokens = if(filterNotName.isNotBlank()) {
            filterNotName.trim().split(",").map { it.trim() }
        } else emptyList()

        val scenarios = feature.scenarios.filter { scenario ->
            if(filterNameTokens.isNotEmpty()) {
                filterNameTokens.any { name -> scenario.testDescription().contains(name) }
            } else true
        }.filter { scenario ->
            if(filterNotNameTokens.isNotEmpty()) {
                filterNotNameTokens.none { name -> scenario.testDescription().contains(name) }
            } else true
        }

        return feature.copy(scenarios = scenarios)
    }

    companion object {
        fun generate(contractFile: File): List<String> {
            val generatedExampleFiles = mutableListOf<String>()

            try {
                val feature = parseContractFileToFeature(contractFile)

                var ctr = 0

                val examplesDir =
                    contractFile.canonicalFile.parentFile.resolve("""${contractFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX""")
                examplesDir.mkdirs()

                feature.executeTests(object : TestExecutor {
                    override fun execute(request: HttpRequest): HttpResponse {
                        ctr += 1

                        val response = feature.lookupResponse(request).cleanup()

                        val scenarioStub = ScenarioStub(request, response)

                        val stubJSON = scenarioStub.toJSON()

                        val stubString = stubJSON.toStringLiteral()

                        val uniqueNameForApiOperation =
                            uniqueNameForApiOperation(scenarioStub.request, "", scenarioStub.response.status)

                        val filename = "${uniqueNameForApiOperation}_${ctr}.json"

                        val file = examplesDir.resolve(filename)
                        val loggablePath =
                            "Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}"

                        println(loggablePath)

                        file.writeText(stubString)
                        generatedExampleFiles.add(file.absolutePath)

                        return response
                    }

                    override fun setServerState(serverState: Map<String, Value>) {
                    }
                })
            } catch (e: StackOverflowError) {
                logger.log("Got a stack overflow error. You probably have a recursive data structure definition in the contract.")
                throw e
            }
            return generatedExampleFiles
        }

        fun generate(
            contractFile: File,
            method: String,
            path: String,
            responseStatusCode: Int,
            contentType: String? = null
        ): List<String> {
            val feature = parseContractFileToFeature(contractFile)
            val examplesDir =
                contractFile.canonicalFile.parentFile.resolve("""${contractFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX""")
            examplesDir.mkdirs()
            val scenario = feature.scenarios.firstOrNull {
                it.method == method && it.status == responseStatusCode && it.path == path
                        && (contentType == null || it.httpRequestPattern.headersPattern.contentType == contentType)
            }
            if(scenario == null) return emptyList()

            val request = scenario.generateHttpRequest()
            val response = feature.lookupResponse(scenario).cleanup()
            val scenarioStub = ScenarioStub(request, response)

            val stubJSON = scenarioStub.toJSON()
            val uniqueNameForApiOperation =
                uniqueNameForApiOperation(scenarioStub.request, "", scenarioStub.response.status)

            val file = examplesDir.resolve("${uniqueNameForApiOperation}.json")
            println("Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}")
            file.writeText(stubJSON.toStringLiteral())
            return listOf(file.absolutePath)
        }

        fun validate(contractFile: File, exampleFile: File): List<HttpStubData> {
            val scenarioStub = ScenarioStub.readFromFile(exampleFile)
            val feature = parseContractFileToFeature(contractFile)

            val result: Pair<Pair<Result.Success, List<HttpStubData>>?, NoMatchingScenario?> =
                HttpStub.setExpectation(scenarioStub, feature, InteractiveExamplesMismatchMessages)
            val validationResult = result.first
            val noMatchingScenario = result.second

            if(validationResult == null) {
                val failures =  noMatchingScenario?.results?.withoutFluff()?.results ?: emptyList()

                val failureResults = Results(failures).withoutFluff()
                throw NoMatchingScenario(
                    failureResults,
                    cachedMessage = failureResults.report(scenarioStub.request),
                    msg = failureResults.report(scenarioStub.request)
                )
            }

            return validationResult.second
        }

        private fun HttpResponse.cleanup(): HttpResponse {
            return this.copy(headers = this.headers.minus(SPECMATIC_RESULT_HEADER))
        }
    }
}

object InteractiveExamplesMismatchMessages : MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Specification expected $expected but example contained $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "$keyLabel $keyName in the example is not in the specification"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "$keyLabel $keyName in the specification is missing from the example"
    }
}

data class ExamplePageRequest(
    val contractFile: String
)

data class ValidateExampleRequest(
    val exampleFiles: List<String>
)

data class ValidateExampleResponse(
    val absPath: String,
    val exampleJson: String,
    val error: String? = null,
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

data class GenerateExampleResponse(
    val generatedExamples: List<String>
)