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
import io.specmatic.core.*
import io.specmatic.core.examples.server.ExamplesView.Companion.groupEndpoints
import io.specmatic.core.examples.server.ExamplesView.Companion.toTableRows
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.core.utilities.*
import io.specmatic.mock.NoMatchingScenario
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.HttpStub
import io.specmatic.stub.HttpStubData
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import kotlin.system.exitProcess

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
                        respondWithExamplePageHtmlContent(contractFile, request.hostPort, call)
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
                            request.contentType
                        )

                        call.respond(HttpStatusCode.OK, GenerateExampleResponse(generatedExample))
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "An unexpected error occurred: ${e.message}")
                    }
                }

                post("/_specmatic/examples/validate") {
                    val request = call.receive<ValidateExampleRequest>()
                    try {
                        val contractFile = getContractFile()
                        val validationResults = try {
                            validate(contractFile, File(request.exampleFile))
                            ValidateExampleResponse(request.exampleFile)
                        } catch (e: FileNotFoundException) {
                            ValidateExampleResponse(request.exampleFile, e.message ?: "File not found")
                        } catch (e: NoMatchingScenario) {
                            ValidateExampleResponse(request.exampleFile, e.msg ?: "Something went wrong")
                        } catch (e: Exception) {
                            ValidateExampleResponse(request.exampleFile, e.message ?: "An unexpected error occurred")
                        }
                        call.respond(HttpStatusCode.OK, validationResults)
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An unexpected error occurred: ${e.message}"))
                    }
                }

                post("/_specmatic/v2/examples/validate") {
                    val request = call.receive<List<ValidateExampleRequest>>()
                    try {
                        val contractFile = getContractFile()
                        val validationResults = request.map {
                            try {
                                validate(contractFile, File(it.exampleFile))
                                ValidateExampleResponseV2(
                                    ValidateExampleVerdict.SUCCESS,
                                    "The provided example is valid",
                                    it.exampleFile
                                )
                            } catch(e: NoMatchingScenario) {
                                ValidateExampleResponseV2(
                                    ValidateExampleVerdict.FAILURE,
                                    e.msg ?: "Something went wrong",
                                    it.exampleFile
                                )
                            } catch(e: Exception) {
                                ValidateExampleResponseV2(
                                    ValidateExampleVerdict.FAILURE,
                                    e.message ?: "An unexpected error occurred",
                                    it.exampleFile
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
        val feature = ScenarioFilter(filterName, filterNotName).filter(parseContractFileToFeature(contractFile))

        val endpoints = ExamplesView.getEndpoints(feature, getExamplesDirPath(contractFile))
        val tableRows = endpoints.groupEndpoints().toTableRows()

        return HtmlTemplateConfiguration.process(
            templateName = "examples/index.html",
            variables = mapOf(
                "tableRows" to tableRows,
                "contractFile" to contractFile.name,
                "contractFilePath" to contractFile.absolutePath,
                "hostPort" to hostPort,
                "hasExamples" to tableRows.any {it.example != null},
                "validationDetails" to tableRows.withIndex().associate { (idx, row) ->
                    idx.inc() to row.exampleMismatchReason
                }
            )
        )
    }

    class ScenarioFilter(filterName: String, filterNotName: String) {
        private val filterNameTokens = if(filterName.isNotBlank()) {
            filterName.trim().split(",").map { it.trim() }
        } else emptyList()

        private val filterNotNameTokens = if(filterNotName.isNotBlank()) {
            filterNotName.trim().split(",").map { it.trim() }
        } else emptyList()

        fun filter(feature: Feature): Feature {
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
    }

    companion object {
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

                val existingExamples = examplesDir.getExamplesFromDir()
                return feature.scenarios.map { scenario ->
                    val existingExample = getExistingExampleFile(scenario, existingExamples)
                    if(existingExample != null) {
                        println("Example already exists for ${scenario.testDescription()} within the file ${existingExample.first.name}, skipping generation.\n")
                        return@map existingExample.first.path
                    }

                    println("Generating example for ${scenario.testDescription()}")
                    val generatedScenario = scenario.generateTestScenarios(DefaultStrategies).first().value

                    val request = generatedScenario.httpRequestPattern.generate(generatedScenario.resolver)
                    val response = generatedScenario.httpResponsePattern.generateResponse(generatedScenario.resolver).cleanup()

                    val scenarioStub = ScenarioStub(request, response)

                    val stubJSON = scenarioStub.toJSON()
                    val uniqueNameForApiOperation =
                        uniqueNameForApiOperation(scenarioStub.request, "", scenarioStub.response.status)

                    val file = examplesDir.resolve("${uniqueNameForApiOperation}.json")
                    println("Writing example to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}")
                    file.writeText(stubJSON.toStringLiteral())

                    file.path
                }.also {
                    println("=============== Example Generation Summary ===============")
                    println("Successfully wrote ${it.size} examples to ${examplesDir.canonicalPath}")
                    println("==========================================================")
                }
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
            contentType: String? = null
        ): String? {
            val feature = parseContractFileToFeature(contractFile)
            val scenario = feature.scenarios.firstOrNull {
                it.method == method && it.status == responseStatusCode && it.path == path
                        && (contentType == null || it.httpRequestPattern.headersPattern.contentType == contentType)
            }
            if(scenario == null) return null

            val examplesDir = getExamplesDirPath(contractFile)
            val existingExampleFile = getExistingExampleFile(scenario, examplesDir.getExamplesFromDir())
            if(existingExampleFile != null) return existingExampleFile.first.absolutePath
            else examplesDir.mkdirs()

            val request = scenario.generateHttpRequest()
            val response = feature.lookupResponse(scenario).cleanup()
            val scenarioStub = ScenarioStub(request, response)

            val stubJSON = scenarioStub.toJSON()
            val uniqueNameForApiOperation =
                uniqueNameForApiOperation(scenarioStub.request, "", scenarioStub.response.status)

            val file = examplesDir.resolve("${uniqueNameForApiOperation}.json")
            println("Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}")
            file.writeText(stubJSON.toStringLiteral())
            return file.absolutePath
        }

        fun validateAll(contractFile: File): Pair<Result?, Map<String, Result>?> {
            val feature = parseContractFileToFeature(contractFile).let {
                ExamplesInteractiveServer.ScenarioFilter("", "/hub,/listener,-> 202").filter(it)
            }

            val (validateInline, validateExternal) = if(!Flags.getBooleanValue("VALIDATE_INLINE_EXAMPLES") && !Flags.getBooleanValue("IGNORE_INLINE_EXAMPLES")) {
                true to true
            } else {
                Flags.getBooleanValue("VALIDATE_INLINE_EXAMPLES") to Flags.getBooleanValue("IGNORE_INLINE_EXAMPLES")
            }

            val inlineResult = if (validateInline) {
                logger.log(System.lineSeparator() + "VALIDATING INLINE EXAMPLES" + System.lineSeparator())
                validateInlineExamples(feature)
            } else null

            val externalResult = if(validateExternal) {
                val examplesDir =
                    contractFile.absoluteFile.parentFile.resolve(contractFile.nameWithoutExtension + "_examples")
                if (!examplesDir.isDirectory) {
                    logger.log("$examplesDir does not exist, did not find any files to validate")
                    exitProcess(1)
                }

                validateExternalExamples(examplesDir, feature)
            }
            else null

            return inlineResult to externalResult
        }

        private fun validateExternalExamples(
            examplesDir: File,
            feature: Feature
        ): Map<String, Result> {
            logger.log("Validating examples in ${examplesDir.path}")

            return examplesDir.walkTopDown().map { file: File ->
                if (file.isDirectory)
                    return@map null

                logger.log("Validating ${file.path}")

                val scenarioStub = ScenarioStub.readFromFile(file)

                val result: Pair<Pair<Result.Success, List<HttpStubData>>?, NoMatchingScenario?> =
                    HttpStub.setExpectation(scenarioStub, feature, InteractiveExamplesMismatchMessages)
                val validationResult = result.first
                val noMatchingScenario = result.second

                if (validationResult != null) {
                    logger.log("Example validation successful for ${file.path}")
                    file.canonicalPath to Result.Success()
                } else {
                    logger.log("Example validation failed for ${file.path}")
                    val failures = noMatchingScenario?.results?.withoutFluff()?.results ?: emptyList()
                    val failureResults = Results(failures).withoutFluff()
                    val cleanedUpFailure = getCleanedUpFailure(failureResults, noMatchingScenario)
                    file.canonicalPath to cleanedUpFailure
                }
            }.filterNotNull().toMap()
        }

        private fun getCleanedUpFailure(
            failureResults: Results,
            noMatchingScenario: NoMatchingScenario?
        ): Result {
            return failureResults.toResultIfAny().let {
                if (it.reportString().isBlank())
                    Result.Failure(noMatchingScenario?.message ?: "")
                else
                    it
            }
        }

        fun validate(contractFile: File, exampleFile: File): List<HttpStubData> {
            val feature = parseContractFileToFeature(contractFile).also {
                validateInlineExamples(it)
            }

            val scenarioStub = ScenarioStub.readFromFile(exampleFile)

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

        private fun validateInlineExamples(it: Feature): Result {
            if (Flags.getBooleanValue("VALIDATE_INLINE_EXAMPLES"))
                try {
                    it.validateExamplesOrException()
                } catch (e: Exception) {
                    return Result.Failure(exceptionCauseMessage(e))
                }

            return Result.Success()
        }

        private fun HttpResponse.cleanup(): HttpResponse {
            return this.copy(headers = this.headers.minus(SPECMATIC_RESULT_HEADER))
        }

        fun getExistingExampleFile(scenario: Scenario, examples: List<ExampleFromFile>): Pair<File, String>? {
            return examples.firstNotNullOfOrNull { example ->
                val response = example.response ?: return@firstNotNullOfOrNull null

                when (val matchResult = scenario.matchesMock(example.request, response)) {
                    is Result.Success -> example.file to ""
                    is Result.Failure -> {
                        val isFailureRelatedToScenario = matchResult.getFailureBreadCrumbs().none { breadCrumb ->
                            breadCrumb.contains(PATH_BREAD_CRUMB)
                                    || breadCrumb.contains(METHOD_BREAD_CRUMB)
                                    || breadCrumb.contains("Content-Type")
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

data class GenerateExampleResponse(
    val generatedExample: String?
)