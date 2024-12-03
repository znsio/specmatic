package io.specmatic.core.examples.server

import com.jayway.jsonpath.JsonPath
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
import io.specmatic.core.discriminator.DiscriminatorExampleInjector
import io.specmatic.core.discriminator.DiscriminatorMetadata
import io.specmatic.core.examples.server.ExamplesView.Companion.toTableRows
import io.specmatic.core.examples.server.ExamplesView.Companion.withSchemaExamples
import io.specmatic.core.examples.server.SchemaExample.Companion.NOT_SCHEMA_BASED
import io.specmatic.core.examples.server.SchemaExample.Companion.SCHEMA_BASED
import io.specmatic.core.examples.server.SchemaExample.Companion.toSchemaExampleFileName
import io.specmatic.core.filters.ScenarioMetadataFilter
import io.specmatic.core.filters.ScenarioMetadataFilter.Companion.filterUsing
import io.specmatic.core.log.consoleDebug
import io.specmatic.core.log.consoleLog
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.core.utilities.capitalizeFirstChar
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.core.value.*
import io.specmatic.mock.MOCK_HTTP_REQUEST
import io.specmatic.mock.MOCK_HTTP_RESPONSE
import io.specmatic.mock.ScenarioStub
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
    externalDictionaryFile: File? = null,
    private val allowOnlyMandatoryKeysInJSONObject: Boolean
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

                post("/_specmatic/examples/generate") {
                    val contractFile = getContractFile()

                    try {
                        val request = call.receive<GenerateExampleRequest>()
                        val generatedExamples = if (request.isSchemaBased) {
                            generateForSchemaBased(contractFile, request.path, request.method)
                        } else {
                            generate(
                                contractFile,
                                request.method,
                                request.path,
                                request.responseStatusCode,
                                request.contentType,
                                request.bulkMode,
                                allowOnlyMandatoryKeysInJSONObject,
                            )
                        }

                        call.respond(HttpStatusCode.OK, GenerateExampleResponse.from(generatedExamples))
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to exceptionCauseMessage(e)))
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
                            else {
                                val breadCrumbs = extractBreadcrumbs(result.reportString())
                                val transformedPath = transformToJsonPaths(breadCrumbs)
                                val lineNumber = getJsonNodeLineNumbersUsingJsonPath(request.exampleFile,transformedPath,breadCrumbs)
//                                val map = mapOf(lineNumber to result.reportString())
                                val map: List<Map<String, Any?>> = listOf(mapOf("lineNumber" to lineNumber, "description" to result.reportString()))
                                ValidateExampleResponseMap(request.exampleFile, map)
                            }
                        } catch (e: FileNotFoundException) {
                            ValidateExampleResponse(request.exampleFile, e.message ?: "File not found")
                        } catch (e: ContractException) {
                            ValidateExampleResponse(request.exampleFile, exceptionCauseMessage(e))
                        } catch (e: Exception) {
                            ValidateExampleResponse(request.exampleFile, e.message ?: "An unexpected error occurred")
                        }
                        call.respond(HttpStatusCode.OK, validationResultResponse)
                    } catch(e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to exceptionCauseMessage(e)))
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

                        val results = validateExamples(contractFile, examples = examples)

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
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to exceptionCauseMessage(e)))
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
    fun extractBreadcrumbs(input: String): List<String> {
        val breadCrumbPrefix = ">> "

        val breadcrumbs = input.lines().map { it.trim() }.filter { it.startsWith(breadCrumbPrefix) }.map { it.removePrefix(
            breadCrumbPrefix
        ) }

        return breadcrumbs
    }



    fun getJsonNodeLineNumbersUsingJsonPath(
        jsonFilePath: String,
        jsonPaths: List<String>,
        breadcrumbs: List<String>
    ): Int? {
        if (jsonPaths.size != breadcrumbs.size) {
            throw IllegalArgumentException("JSON paths and breadcrumbs lists must be of the same size")
        }

        fun transform(path: String): String {
            return "$.${path.replace("/", ".")}"
        }

        val jsonPathString = jsonPaths.firstOrNull()?.let { transform(it) } ?: return null

        return findLineNumber(File(jsonFilePath), JsonPath.compile(jsonPathString))
    }

    fun transformToJsonPaths(breadcrumbs: List<String>): List<String> {
        val jsonPaths: MutableList<String> = ArrayList()

        for (breadcrumb in breadcrumbs) {
            val jsonPath = breadcrumb
                .replace("RESPONSE", "http-response")
                .replace("REQUEST", "http-request")
                .replace("BODY", "body")
                .replace(".", "/")
            jsonPaths.add(jsonPath)
        }

        return jsonPaths
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
        val feature = ScenarioFilter(filterName, filterNotName, filter, filterNot).filter(parseContractFileToFeature(contractFile))

        val examplesDir = getExamplesDirPath(contractFile)
        val endpoints = ExamplesView.getEndpoints(feature, examplesDir)
        val schemaExamplesPairs = examplesDir.getSchemaExamplesWithValidation(feature)
        val tableRows = endpoints.toTableRows().withSchemaExamples(feature, schemaExamplesPairs)

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
                        val examples = getExistingExampleFiles(feature, scenario, examplesDir.getExamplesFromDir())
                            .map { ExamplePathInfo(it.first.file.absolutePath, false) }
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
            bulkMode: Boolean = false,
            allowOnlyMandatoryKeysInJSONObject: Boolean
        ): List<ExamplePathInfo> {
            val feature = parseContractFileToFeature(contractFile)
            val scenario: Scenario? = feature.scenarios.firstOrNull {
                it.method == method && it.status == responseStatusCode && it.path == path
                        && (contentType == null || it.httpRequestPattern.headersPattern.contentType == contentType)
            }
            if(scenario == null) return emptyList()

            val examplesDir = getExamplesDirPath(contractFile)
            val examples = examplesDir.getExamplesFromDir()

            val existingExamples = getExistingExampleFiles(feature, scenario, examples).map { it.first }
            val examplesToCheck = if (bulkMode) existingExamples else emptyList()

            val newExamples = generateExampleFiles(
                contractFile, feature, scenario, allowOnlyMandatoryKeysInJSONObject, existingExamples = examplesToCheck
            )

            return existingExamples.map { ExamplePathInfo(it.file.absolutePath, false) }.plus(newExamples)
        }

        fun generateForSchemaBased(contractFile: File, mainPattern: String, subPattern: String): List<ExamplePathInfo> {
            val examplesDir = getExamplesDirPath(contractFile)
            if(examplesDir.exists().not()) examplesDir.mkdirs()

            val feature = parseContractFileToFeature(contractFile)
            val value = feature.generateSchemaFlagBased(mainPattern, subPattern)
            val schemaFileName = toSchemaExampleFileName(mainPattern, subPattern)

            val exampleFile = examplesDir.getSchemaExamples().firstOrNull {
                it.file.nameWithoutExtension == schemaFileName
            }?.file ?: examplesDir.resolve(schemaFileName)

            println("Writing to file: ${exampleFile.relativeTo(contractFile.canonicalFile.parentFile).path}")
            exampleFile.writeText(value.toStringLiteral())
            return listOf(ExamplePathInfo(path = exampleFile.absolutePath, created = true))
        }

        data class ExamplePathInfo(val path: String, val created: Boolean)

        private fun generateExampleFile(
            contractFile: File,
            feature: Feature,
            scenario: Scenario,
        ): ExamplePathInfo {
            val examplesDir = getExamplesDirPath(contractFile)
            if(!examplesDir.exists()) examplesDir.mkdirs()

            val file = writeToExampleFile(
                ScenarioStub(
                    request = scenario.generateHttpRequest(),
                    response = feature.lookupResponse(scenario).cleanup()
                ),
                contractFile
            )
            return ExamplePathInfo(file.absolutePath, true)
        }


        private fun generateExampleFiles(
            contractFile: File,
            feature: Feature,
            scenario: Scenario,
            allowOnlyMandatoryKeysInJSONObject: Boolean,
            existingExamples: List<ExampleFromFile>
        ): List<ExamplePathInfo> {
            val examplesDir = getExamplesDirPath(contractFile)
            if(!examplesDir.exists()) examplesDir.mkdirs()

            val discriminatorBasedRequestResponses = feature
                .generateDiscriminatorBasedRequestResponseList(
                    scenario,
                    allowOnlyMandatoryKeysInJSONObject = allowOnlyMandatoryKeysInJSONObject
                ).map { it.copy(response = it.response.cleanup()) }

            val requestDiscriminator = discriminatorBasedRequestResponses.first().requestDiscriminator
            val responseDiscriminator = discriminatorBasedRequestResponses.first().responseDiscriminator

            val existingDiscriminators = existingExamples.map {
                it.requestBody?.getDiscriminatorValue(requestDiscriminator).orEmpty() to it.responseBody?.getDiscriminatorValue(responseDiscriminator).orEmpty()
            }.toSet()

            return discriminatorBasedRequestResponses.filterNot { it.matches(existingDiscriminators) }.map { (request, response, requestDiscriminator, responseDiscriminator) ->
                val requestWithoutAttrSelection = request.removeAttrSelection(scenario.attributeSelectionPattern)

                val scenarioStub = ScenarioStub(requestWithoutAttrSelection, response)
                val jsonWithDiscriminator = DiscriminatorExampleInjector(
                    stubJSON = scenarioStub.toJSON(),
                    requestDiscriminator = requestDiscriminator,
                    responseDiscriminator = responseDiscriminator
                ).getExampleWithDiscriminator()

                val uniqueNameForApiOperation = getExampleFileNameBasedOn(
                    requestDiscriminator,
                    responseDiscriminator,
                    scenarioStub
                )

                val file = examplesDir.resolve("${uniqueNameForApiOperation}_${exampleFileNamePostFixCounter.incrementAndGet()}.json")
                println("Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}")
                file.writeText(jsonWithDiscriminator.toStringLiteral())
                ExamplePathInfo(file.absolutePath, true)
            }
        }

        private fun HttpRequest.removeAttrSelection(attributeSelectionPattern: AttributeSelectionPattern): HttpRequest {
            return this.copy(
                queryParams = this.queryParams.remove(attributeSelectionPattern.queryParamKey)
            )
        }

        private fun Value.getDiscriminatorValue(discriminator: DiscriminatorMetadata): String? {
            return when (this) {
                is JSONObjectValue -> {
                    val targetValue = this.getEventValue() ?: this
                    targetValue.findFirstChildByPath(discriminator.discriminatorProperty)?.toStringLiteral()
                }
                is JSONArrayValue -> this.list.first().getDiscriminatorValue(discriminator)
                else -> null
            }
        }

        private fun JSONObjectValue.getEventValue(): JSONObjectValue? {
            return (this.findFirstChildByPath("event") as? JSONObjectValue)?.let { eventValue ->
                eventValue.findFirstChildByPath(eventValue.jsonObject.keys.first()) as? JSONObjectValue
            }
        }

        private fun DiscriminatorBasedRequestResponse.matches(discriminatorValues: Set<Pair<String, String>>): Boolean {
            return discriminatorValues.contains(requestDiscriminator.discriminatorValue to responseDiscriminator.discriminatorValue)
        }

        fun validateSingleExample(contractFile: File, exampleFile: File): Result {
            val feature = parseContractFileToFeature(contractFile)
            return validateSingleExample(feature, exampleFile)
        }

        fun validateSingleExample(feature: Feature, exampleFile: File): Result {
            return kotlin.runCatching {
                val scenarioStub = ScenarioStub.readFromFile(exampleFile)
                validateExample(feature, scenarioStub).toResultIfAny()
            }.getOrElse {
                val schemaExample = SchemaExample(exampleFile)
                feature.matchResultSchemaFlagBased(schemaExample.discriminatorBasedOn, schemaExample.schemaBasedOn, schemaExample.value)
            }
        }

        fun validateExamples(contractFile: File, examples: Map<String, List<ScenarioStub>> = emptyMap(), scenarioFilter: ScenarioFilter = ScenarioFilter()): Map<String, Result> {
            val feature = parseContractFileToFeature(contractFile)
            return validateExamples(feature, examples, false, scenarioFilter)
        }

        fun validateExamples(
            feature: Feature,
            examples: Map<String, List<ScenarioStub>> = emptyMap(),
            inline: Boolean = false,
            scenarioFilter: ScenarioFilter = ScenarioFilter(),
            enableLogging: Boolean = true
        ): Map<String, Result> {
            val updatedFeature = scenarioFilter.filter(feature)

            val results = examples.mapValues { (name, exampleList) ->
                if(enableLogging) logger.log("Validating $name")

                exampleList.mapNotNull { example ->
                    val results = validateExample(updatedFeature, example)
                    if (inline && !results.hasResults()) return@mapNotNull null
                    if (!results.hasResults()) return@mapNotNull Result.Failure(results.report(example.request))

                    results.toResultIfAny()
                }.let {
                    Result.fromResults(it)
                }
            }

            return results
        }

        fun validateExamples(
            feature: Feature,
            examples: List<File> = emptyList(),
            scenarioFilter: ScenarioFilter = ScenarioFilter(),
            enableLogging: Boolean = true
        ): Map<String, Result> {
            val updatedFeature = scenarioFilter.filter(feature)

            val results = examples.mapNotNull { example ->
                if (enableLogging) logger.log("Validating ${example.name}")

                val result = kotlin.runCatching {
                    val scenarioStub = ScenarioStub.readFromFile(example)
                    validateExample(updatedFeature, scenarioStub).toResultIfAny()
                }.getOrElse {
                    val schemaExample = SchemaExample(example)
                    if (schemaExample.value !is NullValue) {
                        updatedFeature.matchResultSchemaFlagBased(schemaExample.discriminatorBasedOn, schemaExample.schemaBasedOn, schemaExample.value)
                    } else {
                        if (enableLogging) logger.log("Skipping empty schema example ${example.name}"); null
                    }
                } ?: return@mapNotNull null

                example.name to result
            }.toMap()

            return results
        }

        private fun validateExample(
            feature: Feature,
            scenarioStub: ScenarioStub
        ): Results {
            return feature.matchResultFlagBased(scenarioStub, InteractiveExamplesMismatchMessages)
        }

        private fun HttpResponse.cleanup(): HttpResponse {
            return this.copy(headers = this.headers.minus(SPECMATIC_RESULT_HEADER))
        }

        fun getExistingExampleFiles(feature: Feature, scenario: Scenario, examples: List<ExampleFromFile>): List<Pair<ExampleFromFile, String>> {
            return examples.mapNotNull { example ->
                when (val matchResult = scenario.matches(example.request, example.response, InteractiveExamplesMismatchMessages, feature.flagsBased)) {
                    is Result.Success -> example to ""
                    is Result.Failure -> {
                        val isFailureRelatedToScenario = matchResult.getFailureBreadCrumbs("").none { breadCrumb ->
                            breadCrumb.contains(PATH_BREAD_CRUMB)
                                    || breadCrumb.contains(METHOD_BREAD_CRUMB)
                                    || breadCrumb.contains("REQUEST.HEADERS.Content-Type")
                                    || breadCrumb.contains("STATUS")
                        }
                        if (isFailureRelatedToScenario) example to matchResult.reportString() else null
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
            return this.listFiles()?.mapNotNull {
                runCatching {
                    ExampleFromFile(it)
                }.onFailure { err ->
                    val isExampleSchemaBased = err is ContractException && err.breadCrumb == SCHEMA_BASED
                    if (!isExampleSchemaBased) consoleDebug(exceptionCauseMessage(err))
                }.getOrNull()
            } ?: emptyList()
        }

        fun File.getSchemaExamplesWithValidation(feature: Feature): List<Pair<SchemaExample, String?>> {
            return getSchemaExamples().map {
                it to if(it.value !is NullValue) {
                    feature.matchResultSchemaFlagBased(it.discriminatorBasedOn, it.schemaBasedOn, it.value).reportString()
                } else null
            }
        }

        private fun File.getSchemaExamples(): List<SchemaExample> {
            return this.listFiles()?.mapNotNull { exampleFile ->
                runCatching {
                    SchemaExample(exampleFile)
                }.onFailure { err ->
                    val isExampleSchemaBased = err is ContractException && err.breadCrumb != NOT_SCHEMA_BASED
                    if (isExampleSchemaBased) consoleDebug(exceptionCauseMessage(err))
                }.getOrNull()
            } ?: emptyList()
        }

        private fun getExampleFileNameBasedOn(
            requestDiscriminator: DiscriminatorMetadata,
            responseDiscriminator: DiscriminatorMetadata,
            scenarioStub: ScenarioStub
        ): String {
            val discriminatorValue = requestDiscriminator.discriminatorValue.ifBlank {
                responseDiscriminator.discriminatorValue
            }
            val discriminatorName = if (discriminatorValue.isNotEmpty()) "${discriminatorValue}_" else ""
            return discriminatorName + uniqueNameForApiOperation(
                scenarioStub.request,
                "",
                scenarioStub.response.status
            )
        }

        private fun Value.isScalarOrEmpty(): Boolean {
            return this is ScalarValue || this is NoBodyValue
        }

        private fun Value.insertFieldsInValue(fieldsToBeInserted: Map<String, Value>): Value {
            return when (this) {
                is JSONObjectValue -> JSONObjectValue(fieldsToBeInserted.plus(this.jsonObject))
                is JSONArrayValue -> JSONArrayValue(this.list.map {value ->  value.insertFieldsInValue(fieldsToBeInserted) })
                else -> this
            }
        }

        private fun Map<String, String>.toValueMap(): Map<String, Value> {
            return this.mapValues { StringValue(it.value) }
        }

        fun transformExistingExamples(contractFile: File, overlayFile: File?, examplesDir: File) {
            val feature = parseContractFileToFeature(contractPath = contractFile.absolutePath, overlayContent = overlayFile?.readText().orEmpty())
            val examples = examplesDir.getExamplesFromDir()

            examples.forEach { example ->
                consoleDebug("\nTransforming ${example.file.nameWithoutExtension}")

                if (example.request.body.isScalarOrEmpty() && example.response.body.isScalarOrEmpty()) {
                    consoleDebug("Skipping ${example.file.name}, both request and response bodies are scalars")
                    return@forEach
                }

                val scenario = feature.matchResultFlagBased(example.request, example.response, InteractiveExamplesMismatchMessages)
                    .toResultIfAny().takeIf { it.isSuccess() }?.scenario as? Scenario

                if (scenario == null) {
                    consoleDebug("Skipping ${example.file.name}, no matching scenario found")
                    return@forEach
                }

                val flagBasedResolver = feature.flagsBased.update(scenario.resolver)
                val requestWithoutOptionality = scenario.httpRequestPattern.withoutOptionality(example.request, flagBasedResolver)
                val responseWithoutOptionality = scenario.httpResponsePattern.withoutOptionality(example.response, flagBasedResolver)

                val updatedExample = example.replaceWithDescriptions(requestWithoutOptionality, responseWithoutOptionality)
                consoleDebug("Writing transformed example to ${example.file.canonicalFile.relativeTo(contractFile).path}")
                example.file.writeText(updatedExample.toStringLiteral())
                consoleDebug("Successfully written transformed example")
            }
        }

        private fun ExampleFromFile.replaceWithDescriptions(request: HttpRequest, response: HttpResponse): JSONObjectValue {
            return this.json.jsonObject.mapValues { (key, value) ->
                when (key) {
                    MOCK_HTTP_REQUEST -> request.toJSON().insertFieldsInValue(value.getDescriptionMap())
                    MOCK_HTTP_RESPONSE -> response.toJSON().insertFieldsInValue(value.getDescriptionMap())
                    else -> value
                }
            }.let { JSONObjectValue(it.toMap()) }
        }

        private fun Value.getDescriptionMap(): Map<String, Value> {
            return (this as? JSONObjectValue)
                ?.findFirstChildByPath("description")
                ?.let { mapOf("description" to it.toStringLiteral()).toValueMap() }
                ?: emptyMap()
        }

        fun externaliseInlineExamples(contractFile: File): File {
            val feature = parseContractFileToFeature(contractFile)
            val inlineStubs: List<ScenarioStub> = feature.stubsFromExamples.flatMap {
                it.value.map { (request, response) -> ScenarioStub(request, response) }
            }
            try {
                inlineStubs.forEach { writeToExampleFile(it, contractFile) }
            } catch(e: Exception) {
                consoleLog(e)
            }
            return getExamplesDirPath(contractFile)
        }

        private fun writeToExampleFile(
            scenarioStub: ScenarioStub,
            contractFile: File
        ): File {
            val examplesDir = getExamplesDirPath(contractFile)
            if(examplesDir.exists().not()) examplesDir.mkdirs()
            val stubJSON = scenarioStub.toJSON()
            val uniqueNameForApiOperation =
                uniqueNameForApiOperation(scenarioStub.request, "", scenarioStub.response.status)

            val file = examplesDir.resolve("${uniqueNameForApiOperation}_${exampleFileNamePostFixCounter.incrementAndGet()}.json")
            println("Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}")
            file.writeText(stubJSON.toStringLiteral())
            return file
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

data class SaveExampleRequest(
    val exampleFile: String,
    val exampleContent: String
)

data class ValidateExampleResponse(
    val absPath: String,
    val error: String? = null
)

data class ValidateExampleResponseMap(
    val absPath: String,
    val error: List<Map<String, Any?>> = emptyList()
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
    val contentType: String? = null,
    val bulkMode: Boolean = false,
    val isSchemaBased: Boolean = false
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
