package io.specmatic.test

import com.fasterxml.jackson.databind.ObjectMapper
import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.*
import io.specmatic.core.log.ignoreLog
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.*
import io.specmatic.core.utilities.*
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value
import io.specmatic.stub.hasOpenApiFileExtension
import io.specmatic.stub.isOpenAPI
import io.specmatic.test.SpecmaticJUnitSupport.URIValidationResult.*
import io.specmatic.test.reports.OpenApiCoverageReportProcessor
import io.specmatic.test.reports.coverage.Endpoint
import io.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import io.specmatic.test.reports.coverage.html.HtmlReport
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.opentest4j.TestAbortedException
import java.io.File
import java.lang.management.ManagementFactory
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.*
import java.util.stream.Stream
import javax.management.ObjectName
import kotlin.streams.asStream


interface ContractTestStatisticsMBean {
    fun testsExecuted(): Int
}

class ContractTestStatistics : ContractTestStatisticsMBean {
    override fun testsExecuted(): Int = SpecmaticJUnitSupport.openApiCoverageReportInput.testResultRecords.size
}

@Serializable
data class API(val method: String, val path: String)

@Execution(ExecutionMode.CONCURRENT)
open class SpecmaticJUnitSupport {
    companion object {
        const val CONTRACT_PATHS = "contractPaths"
        const val WORKING_DIRECTORY = "workingDirectory"
        const val CONFIG_FILE_NAME = "manifestFile"
        const val INLINE_SUGGESTIONS = "suggestions"
        const val SUGGESTIONS_PATH = "suggestionsPath"
        const val HOST = "host"
        const val PORT = "port"
        const val PROTOCOL = "protocol"
        const val TEST_BASE_URL = "testBaseURL"
        const val ENV_NAME = "environment"
        const val VARIABLES_FILE_NAME = "variablesFileName"
        const val FILTER_NAME_PROPERTY = "filterName"
        const val FILTER_NOT_NAME_PROPERTY = "filterNotName"
        const val FILTER_NAME_ENVIRONMENT_VARIABLE = "FILTER_NAME"
        const val FILTER_NOT_NAME_ENVIRONMENT_VARIABLE = "FILTER_NOT_NAME"
        private const val ENDPOINTS_API = "endpointsAPI"

        val partialSuccesses: MutableList<Result.Success> = mutableListOf()
        private var specmaticConfig: SpecmaticConfig? = null
        val openApiCoverageReportInput = OpenApiCoverageReportInput(getConfigFileWithAbsolutePath())

        private val threads: Vector<String> = Vector<String>()

        @AfterAll
        @JvmStatic
        fun report() {
            val reportProcessors = listOf(OpenApiCoverageReportProcessor(openApiCoverageReportInput))
            try {
                reportProcessors.forEach { it.process(getReportConfiguration()) }
            } finally {
                HtmlReport(specmaticConfig?.report).generate()
            }

            threads.distinct().let {
                if(it.size > 1) {
                    logger.newLine()
                    logger.log("Executed tests in ${it.size} threads")
                }
            }
        }

        private fun getReportConfiguration(): ReportConfiguration {
            val defaultFormatters = listOf(ReportFormatter(ReportFormatterType.TEXT, ReportFormatterLayout.TABLE))
            val defaultReportTypes = ReportTypes(apiCoverage = APICoverage(openAPI = APICoverageConfiguration(successCriteria = SuccessCriteria(0, 0, false))))
            return when (val reportConfiguration = specmaticConfig?.report) {
                null -> {
                    logger.log("Could not load report configuration, coverage will be calculated but no coverage threshold will be enforced")
                    ReportConfiguration(formatters = defaultFormatters, types = defaultReportTypes)
                }
                else -> {
                    reportConfiguration.copy(formatters = reportConfiguration.formatters ?: defaultFormatters)
                }
            }
        }

        fun queryActuator() {
            val endpointsAPI = System.getProperty(ENDPOINTS_API)

            if(endpointsAPI != null) {
                val request = HttpRequest("GET")

                val response = HttpClient(endpointsAPI, log = ignoreLog).execute(request)

                logger.debug(response.toLogString())

                openApiCoverageReportInput.setEndpointsAPIFlag(true)

                val endpointData = response.body as JSONObjectValue
                val apis: List<API> = endpointData.getJSONObject("contexts").entries.flatMap { entry ->
                    val mappings: JSONArrayValue =
                        (entry.value as JSONObjectValue).findFirstChildByPath("mappings.dispatcherServlets.dispatcherServlet") as JSONArrayValue
                    mappings.list.map { it as JSONObjectValue }.filter {
                        it.findFirstChildByPath("details.handlerMethod.className")?.toStringLiteral()
                            ?.contains("springframework") != true
                    }.flatMap {
                        val methods: JSONArrayValue? =
                            it.findFirstChildByPath("details.requestMappingConditions.methods") as JSONArrayValue?
                        val paths: JSONArrayValue? =
                            it.findFirstChildByPath("details.requestMappingConditions.patterns") as JSONArrayValue?

                        if(methods != null && paths != null) {
                            methods.list.flatMap { method ->
                                paths.list.map { path ->
                                    API(method.toStringLiteral(), path.toStringLiteral())
                                }
                            }
                        } else {
                            emptyList()
                        }
                    }
                }

                openApiCoverageReportInput.addAPIs(apis)

            } else {
                logger.log("Endpoints API not found, cannot calculate actual coverage")
            }
        }

        val configFile get() = System.getProperty(CONFIG_FILE_NAME) ?: getConfigFileName()

        private fun getConfigFileWithAbsolutePath() = File(configFile).canonicalPath
    }

    private fun getEnvConfig(envName: String?): JSONObjectValue {
        if(envName.isNullOrBlank())
            return JSONObjectValue()

        val configFileName = getConfigFileName()
        if(!File(configFileName).exists())
            throw ContractException("Environment name $envName was specified but config file does not exist in the project root. Either avoid setting envName, or provide the configuration file with the environment settings.")

        val config = loadSpecmaticConfig(configFileName)

        val envConfigFromFile = config.environments?.get(envName) ?: return JSONObjectValue()

        try {
            return parsedJSONObject(content = ObjectMapper().writeValueAsString(envConfigFromFile))
        } catch(e: Throwable) {
            throw ContractException("Error loading Specmatic configuration: ${e.message}")
        }
    }

    private fun loadExceptionAsTestError(e: Throwable): Stream<DynamicTest> {
        return sequenceOf(DynamicTest.dynamicTest("Load Error") {
            ResultAssert.assertThat(Result.Failure(exceptionCauseMessage(e))).isSuccess()
        }).asStream()
    }

    @TestFactory
    fun contractTest(): Stream<DynamicTest> {
        val statistics = ContractTestStatistics()
        var name = ObjectName("io.specmatic:type=ContractTestStatistics")

        var mbs = ManagementFactory.getPlatformMBeanServer()

        if(!mbs.isRegistered(name))
            mbs.registerMBean(statistics, name)

        val contractPaths = System.getProperty(CONTRACT_PATHS)
        val givenWorkingDirectory = System.getProperty(WORKING_DIRECTORY)
        val filterName: String? = System.getProperty(FILTER_NAME_PROPERTY) ?: System.getenv(FILTER_NAME_ENVIRONMENT_VARIABLE)
        val filterNotName: String? = System.getProperty(FILTER_NOT_NAME_PROPERTY) ?: System.getenv(FILTER_NOT_NAME_ENVIRONMENT_VARIABLE)

        val timeoutInMilliseconds = specmaticConfig?.test?.timeoutInMilliseconds ?: DEFAULT_TIMEOUT_IN_MILLISECONDS

        val suggestionsData = System.getProperty(INLINE_SUGGESTIONS) ?: ""
        val suggestionsPath = System.getProperty(SUGGESTIONS_PATH) ?: ""

        val workingDirectory = WorkingDirectory(givenWorkingDirectory ?: DEFAULT_WORKING_DIRECTORY)

        val envConfig = getEnvConfig(System.getProperty(ENV_NAME))
        val testConfig = try {
            loadTestConfig(envConfig).withVariablesFromFilePath(System.getProperty(VARIABLES_FILE_NAME))
        } catch (e: Throwable) {
            return loadExceptionAsTestError(e)
        }
        val testScenarios = try {
            val (testScenarios, allEndpoints) = when {
                contractPaths != null -> {
                    val testScenariosAndEndpointsPairList = contractPaths.split(",").filter {
                        File(it).extension in CONTRACT_EXTENSIONS
                    }.map {
                        loadTestScenarios(
                            it,
                            suggestionsPath,
                            suggestionsData,
                            testConfig,
                            specificationPath = it,
                            filterName = filterName,
                            filterNotName = filterNotName
                        )
                    }
                    val tests: Sequence<ContractTest> = testScenariosAndEndpointsPairList.asSequence().flatMap { it.first }
                    val endpoints: List<Endpoint> = testScenariosAndEndpointsPairList.flatMap { it.second }
                    Pair(tests, endpoints)
                }
                else -> {
                    val configFile = configFile

                    exitIfDoesNotExist("config file", configFile)

                    createIfDoesNotExist(workingDirectory.path)

                    specmaticConfig = getSpecmaticConfig()

                    val contractFilePaths = contractTestPathsFrom(configFile, workingDirectory.path)

                    exitIfAnyDoNotExist("The following specifications do not exist", contractFilePaths.map { it.path })

                    val testScenariosAndEndpointsPairList = contractFilePaths.filter {
                        File(it.path).extension in CONTRACT_EXTENSIONS
                    }.map {
                        loadTestScenarios(
                            it.path,
                            "",
                            "",
                            testConfig,
                            it.provider,
                            it.repository,
                            it.branch,
                            it.specificationPath,
                            specmaticConfig?.security,
                            filterName,
                            filterNotName,
                            specmaticConfig = specmaticConfig
                        )
                    }

                    val tests: Sequence<ContractTest> = testScenariosAndEndpointsPairList.asSequence().flatMap { it.first }

                    val endpoints: List<Endpoint> = testScenariosAndEndpointsPairList.flatMap { it.second }

                    Pair(tests, endpoints)
                }
            }
            openApiCoverageReportInput.addEndpoints(allEndpoints)
            selectTestsToRun(testScenarios, filterName, filterNotName) { it.testDescription() }
        } catch(e: ContractException) {
            return loadExceptionAsTestError(e)
        } catch(e: Throwable) {
            return loadExceptionAsTestError(e)
        }

        val testBaseURL = try {
            constructTestBaseURL()
        } catch (e: Throwable) {
            logger.logError(e)
            logger.newLine()
            throw(e)
        }

        return try {
            dynamicTestStream(testScenarios, testBaseURL, timeoutInMilliseconds)
        } catch(e: Throwable) {
            logger.logError(e)
            loadExceptionAsTestError(e)
        }
    }

    private fun dynamicTestStream(
        testScenarios: Sequence<ContractTest>,
        testBaseURL: String,
        timeoutInMilliseconds: Long
    ): Stream<DynamicTest> {
        try {
            queryActuator()
        } catch (exception: Throwable) {
            logger.log(exception, "Failed to query actuator with error")
        }

        logger.newLine()

        return testScenarios.map { contractTest ->
            DynamicTest.dynamicTest(contractTest.testDescription()) {
                threads.add(Thread.currentThread().name)

                var testResult: Pair<Result, HttpResponse?>? = null

                try {
                    testResult = contractTest.runTest(testBaseURL, timeoutInMilliseconds)
                    val (result, response) = testResult

                    if (result is Result.Success && result.isPartialSuccess()) {
                        partialSuccesses.add(result)
                    }

                    when {
                        result.shouldBeIgnored() -> {
                            val message =
                                "Test FAILED, ignoring since the scenario is tagged @WIP${System.lineSeparator()}${
                                    result.toReport().toText().prependIndent("  ")
                                }"
                            throw TestAbortedException(message)
                        }

                        else -> ResultAssert.assertThat(result).isSuccess()
                    }

                } catch (e: Throwable) {
                    throw e
                } finally {
                    if (testResult != null) {
                        val (result, response) = testResult
                        contractTest.testResultRecord(result, response)?.let { testREsultRecord -> openApiCoverageReportInput.addTestReportRecords(testREsultRecord) }
                    }
                }
            }
        }.asStream()
    }

    fun constructTestBaseURL(): String {
        val testBaseURL = System.getProperty(TEST_BASE_URL)
        if (testBaseURL != null) {
            when (val validationResult = validateURI(testBaseURL)) {
                Success -> return testBaseURL
                else -> throw TestAbortedException("${validationResult.message} in $TEST_BASE_URL environment variable")
            }
        }

        val hostProperty = System.getProperty(HOST)
            ?: throw TestAbortedException("Please specify $TEST_BASE_URL OR $HOST and $PORT as environment variables")
        val host = if (hostProperty.startsWith("http")) {
            URI(hostProperty).host
        } else {
            hostProperty
        }
        val protocol = System.getProperty(PROTOCOL) ?: "http"
        val port = System.getProperty(PORT)

        if (!isNumeric(port)) {
            throw TestAbortedException("Please specify a number value for $PORT environment variable")
        }

        val urlConstructedFromProtocolHostAndPort = "$protocol://$host:$port"

        return when (validateURI(urlConstructedFromProtocolHostAndPort)) {
            Success -> urlConstructedFromProtocolHostAndPort
            else -> throw TestAbortedException("Please specify a valid $PROTOCOL, $HOST and $PORT environment variables")
        }
    }

    private fun isNumeric(port: String?): Boolean {
        return port?.toIntOrNull() != null
    }

    enum class URIValidationResult(val message: String) {
        URIParsingError("Please specify a valid URL"),
        InvalidURLSchemeError("Please specify a valid scheme / protocol (http or https)"),
        InvalidPortError("Please specify a valid port number"),
        Success("This URL is valid");
    }

    fun validateURI(uri: String): URIValidationResult {
        val parsedURI = try {
            URL(uri).toURI()
        } catch (e: URISyntaxException) {
            return URIParsingError
        } catch(e: MalformedURLException) {
            return URIParsingError
        }

        val validProtocols = listOf("http", "https")
        val validPorts = 1..65535

        return when {
            !validProtocols.contains(parsedURI.scheme) -> InvalidURLSchemeError
            parsedURI.port != -1 && !validPorts.contains(parsedURI.port) -> InvalidPortError

            else -> Success
        }
    }

    private fun portNotSpecified(parsedURI: URI) = parsedURI.port == -1

    fun loadTestScenarios(
        path: String,
        suggestionsPath: String,
        suggestionsData: String,
        config: TestConfig,
        sourceProvider: String? = null,
        sourceRepository: String? = null,
        sourceRepositoryBranch: String? = null,
        specificationPath: String? = null,
        securityConfiguration: SecurityConfiguration? = null,
        filterName: String?,
        filterNotName: String?,
        specmaticConfig: SpecmaticConfig? = null
    ): Pair<Sequence<ContractTest>, List<Endpoint>> {
        if(hasOpenApiFileExtension(path) && !isOpenAPI(path))
            return Pair(emptySequence(), emptyList())

        val contractFile = File(path)
        val feature =
            parseContractFileToFeature(
                contractFile.path,
                CommandHook(HookName.test_load_contract),
                sourceProvider,
                sourceRepository,
                sourceRepositoryBranch,
                specificationPath,
                securityConfiguration,
                specmaticConfig = specmaticConfig ?: SpecmaticConfig()
            ).copy(testVariables = config.variables, testBaseURLs = config.baseURLs).loadExternalisedExamples()

        feature.validateExamplesOrException()

        val suggestions = when {
            suggestionsPath.isNotEmpty() -> suggestionsFromFile(suggestionsPath)
            suggestionsData.isNotEmpty() -> suggestionsFromCommandLine(suggestionsData)
            else -> emptyList()
        }

        val allEndpoints: List<Endpoint> = feature.scenarios.map { scenario ->
            Endpoint(
                convertPathParameterStyle(scenario.path),
                scenario.method,
                scenario.httpResponsePattern.status,
                scenario.sourceProvider,
                scenario.sourceRepository,
                scenario.sourceRepositoryBranch,
                scenario.specification,
                scenario.serviceType
            )
        }

        val tests: Sequence<ContractTest> = feature
            .copy(scenarios = selectTestsToRun(feature.scenarios.asSequence(), filterName, filterNotName) { it.testDescription() }.toList())
            .also {
                if (it.scenarios.isEmpty())
                    logger.log("All scenarios were filtered out.")
                else if (it.scenarios.size < feature.scenarios.size) {
                    logger.debug("Selected scenarios:")
                    it.scenarios.forEach { scenario -> logger.debug(scenario.testDescription().prependIndent("  ")) }
                }
            }
            .generateContractTests(suggestions)

        return Pair(tests, allEndpoints)
    }

    private fun getSpecmaticConfig(): SpecmaticConfig? {
        return try {
            loadSpecmaticConfig(configFile)
        }
        catch (e: ContractException) {
            logger.log(exceptionCauseMessage(e))
            null
        }
        catch (e: Throwable) {
            exitWithMessage(exceptionCauseMessage(e))
        }
    }

    private fun suggestionsFromFile(suggestionsPath: String): List<Scenario> {
        return Suggestions.fromFile(suggestionsPath).scenarios
    }

    private fun suggestionsFromCommandLine(suggestions: String): List<Scenario> {
        val suggestionsValue = parsedValue(suggestions)
        if (suggestionsValue !is JSONObjectValue)
            throw ContractException("Suggestions must be a json value with scenario name as the key, and json array with 1 or more json objects containing suggestions")

        return suggestionsValue.jsonObject.mapValues { (_, exampleData) ->
            when {
                exampleData !is JSONArrayValue -> throw ContractException("The value of a scenario must be a list of examples")
                exampleData.list.isEmpty() -> Examples()
                else -> {
                    val columns = columnsFromExamples(exampleData)

                    val rows = exampleData.list.map { row ->
                        asJSONObjectValue(row)
                    }.map { row ->
                        Row(columns, columns.map { row.getValue(it).toStringLiteral() })
                    }.toMutableList()

                    Examples(columns, rows)
                }
            }
        }.entries.map { (name, examples) ->
            Scenario(
                name,
                HttpRequestPattern(),
                HttpResponsePattern(),
                emptyMap(),
                listOf(examples),
                emptyMap(),
                emptyMap(),
            )
        }
    }
}

private fun columnsFromExamples(exampleData: JSONArrayValue): List<String> {
    val firstRow = exampleData.list[0]
    if (firstRow !is JSONObjectValue)
        throw ContractException("Each value in the list of suggestions must be a json object containing column name as key and sample value as the value")

    return firstRow.jsonObject.keys.toList()
}

private fun asJSONObjectValue(value: Value): Map<String, Value> {
    val errorMessage = "Each value in the list of suggestions must be a json object containing column name as key and sample value as the value"
    if(value !is JSONObjectValue)
        throw ContractException(errorMessage)

    return value.jsonObject
}

fun <T> selectTestsToRun(
    testScenarios: Sequence<T>,
    filterName: String? = null,
    filterNotName: String? = null,
    getTestDescription: (T) -> String
): Sequence<T> {
    val filteredByName = if (!filterName.isNullOrBlank()) {
        val filterNames = filterName.split(",").map { it.trim() }

        testScenarios.filter { test ->
            filterNames.any { getTestDescription(test).contains(it) }
        }
    } else
        testScenarios

    val filteredByNotName: Sequence<T> = if(!filterNotName.isNullOrBlank()) {
        val filterNotNames = filterNotName.split(",").map { it.trim() }

        filteredByName.filterNot { test ->
            filterNotNames.any { getTestDescription(test).contains(it) }
        }
    } else
        filteredByName

    return filteredByNotName
}
