package io.specmatic.proxy

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.specmatic.core.EXAMPLES_DIR_SUFFIX
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.Results
import io.specmatic.core.SPECMATIC_RESULT_HEADER
import io.specmatic.core.log.logger
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.core.value.Value
import io.specmatic.mock.NoMatchingScenario
import io.specmatic.mock.ScenarioStub
import io.specmatic.proxy.ExamplesView.Companion.groupEndpoints
import io.specmatic.proxy.ExamplesView.Companion.toTableRows
import io.specmatic.stub.HttpStub
import io.specmatic.stub.HttpStubData
import io.specmatic.test.TestExecutor
import java.io.Closeable
import java.io.File

class ExamplesInteractiveServer(
    private val serverHost: String,
    private val serverPort: Int,
    private val contractFile: File
) : Closeable {
    private val environment = applicationEngineEnvironment {
        module {
            configureHealthCheckModule()
            routing {
                get("/_specmatic/examples") {
                    val feature = parseContractFileToFeature(contractFile)
                    val endpoints = ExamplesView.getEndpoints(feature)

                    val html = HtmlTemplateConfiguration.process(
                        templateName = "examples/index.html",
                        variables = mapOf("endpoints" to endpoints.groupEndpoints().toTableRows())
                    )

                    call.respondText(html, contentType = ContentType.Text.Html)
                }

                post("/_specmatic/examples") {
                    try {
                        val request = call.receive<GenerateExampleRequest>()
                        val generatedExamples = generate(
                            contractFile,
                            request.method,
                            request.path,
                            request.responseStatusCode
                        )
                        call.respond(HttpStatusCode.OK, GenerateExampleResponse(generatedExamples))
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
                    }
                }

                post("/_specmatic/examples/validate") {
                    val request = call.receive<ValidateExampleRequest>()
                    try {
                        validate(
                            contractFile,
                            File(request.exampleFile)
                        )
                        call.respond(HttpStatusCode.OK, "The provided example is valid.")
                    } catch(e: NoMatchingScenario) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.msg))
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
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
            responseStatusCode: Int
        ): MutableList<String> {
            val generatedExampleFiles = mutableListOf<String>()
            val feature = parseContractFileToFeature(contractFile)
            val examplesDir =
                contractFile.canonicalFile.parentFile.resolve("""${contractFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX""")
            examplesDir.mkdirs()

            var ctr = 0
            feature.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val response = feature.lookupResponse(request).cleanup()

                    val scenarioStub = ScenarioStub(request, response)

                    val stubJSON = scenarioStub.toJSON()

                    val stubString = stubJSON.toStringLiteral()

                    val uniqueNameForApiOperation =
                        uniqueNameForApiOperation(scenarioStub.request, "", scenarioStub.response.status)

                    if(request.path == path && request.method == method && response.status == responseStatusCode) {
                        ctr += 1
                        val file = examplesDir.resolve("${uniqueNameForApiOperation}_$ctr.json")
                        val loggablePath =
                            "Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}"
                        println(loggablePath)
                        file.writeText(stubString)
                        generatedExampleFiles.add(file.absolutePath)
                    }

                    return response
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })

            return generatedExampleFiles
        }

        fun validate(contractFile: File, exampleFile: File): List<HttpStubData> {
            val scenarioStub = ScenarioStub.readFromFile(exampleFile)
            val feature = parseContractFileToFeature(contractFile)

            val result: Pair<Pair<Result.Success, List<HttpStubData>>?, NoMatchingScenario?> =
                HttpStub.setExpectation(scenarioStub, feature)
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

data class ValidateExampleRequest(
    val exampleFile: String
)

data class GenerateExampleRequest(
    val method: String,
    val path: String,
    val responseStatusCode: Int
)

data class GenerateExampleResponse(
    val generatedExamples: List<String>
)