package `in`.specmatic.test

import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.*
import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.log.ignoreLog
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.isOpenAPI
import `in`.specmatic.stub.hasOpenApiFileExtension
import `in`.specmatic.test.reports.OpenApiCoverageReportProcessor
import `in`.specmatic.test.reports.coverage.Endpoint
import `in`.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.opentest4j.TestAbortedException
import java.io.File
import java.util.*

@Serializable
data class API(val method: String, val path: String)

@Execution(ExecutionMode.CONCURRENT)
open class SpecmaticJUnitSupport {
    companion object {
        const val CONTRACT_PATHS = "contractPaths"
        const val WORKING_DIRECTORY = "workingDirectory"
        const val CONFIG_FILE_NAME = "manifestFile"
        const val TIMEOUT = "timeout"
        private const val DEFAULT_TIMEOUT = "60"
        const val INLINE_SUGGESTIONS = "suggestions"
        const val SUGGESTIONS_PATH = "suggestionsPath"
        const val HOST = "host"
        const val PORT = "port"
        const val TEST_BASE_URL = "testBaseURL"
        const val ENV_NAME = "environment"
        const val VARIABLES_FILE_NAME = "variablesFileName"
        const val FILTER_NAME = "filterName"
        const val FILTER_NOT_NAME = "filterNotName"
        private const val ENDPOINTS_API = "endpointsAPI"

        val partialSuccesses: MutableList<Result.Success> = mutableListOf()
        private var specmaticConfigJson: SpecmaticConfigJson? = null
        private val openApiCoverageReportInput = OpenApiCoverageReportInput(getConfigFileWithAbsolutePath())

        private val threads: Vector<String> = Vector<String>()

        var totalTestCount: Int = 0


        @AfterAll
        @JvmStatic
        fun report() {
            val reportProcessors = listOf(OpenApiCoverageReportProcessor(openApiCoverageReportInput))
            reportProcessors.forEach { it.process(getReportConfiguration()) }

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
            return when (val reportConfiguration = specmaticConfigJson?.report) {
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

        val configFile get() = System.getProperty(CONFIG_FILE_NAME) ?: getGlobalConfigFileName()

        private fun getConfigFileWithAbsolutePath() = File(configFile).canonicalPath
    }

    private fun getEnvConfig(envName: String?): JSONObjectValue {
        if(envName.isNullOrBlank())
            return JSONObjectValue()

        val configFile = File(globalConfigFileName)
        if(!configFile.exists())
            throw ContractException("Environment name $envName was specified but config file (usually named specmatic.json) does not exist in the project root. Either avoid setting envName, or provide specmatic.json with the environment settings.")

        val config = loadConfigJSON(configFile)
        val envConfig = config.findFirstChildByPath("environments.$envName") ?: return JSONObjectValue()

        if(envConfig !is JSONObjectValue)
            throw ContractException("The environment config must be a JSON object.")

        return envConfig
    }

    private fun loadExceptionAsTestError(e: Throwable): Collection<DynamicTest> {
        return listOf(DynamicTest.dynamicTest("Load Error") {
            logger.log(e)
            ResultAssert.assertThat(Result.Failure(exceptionCauseMessage(e))).isSuccess()
        })
    }

    @TestFactory
    fun contractTest(): Collection<DynamicTest> {
        val contractPaths = System.getProperty(CONTRACT_PATHS)
        val givenWorkingDirectory = System.getProperty(WORKING_DIRECTORY)
        val filterName: String? = System.getProperty(FILTER_NAME)
        val filterNotName: String? = System.getProperty(FILTER_NOT_NAME)

        val timeout = System.getProperty(TIMEOUT, DEFAULT_TIMEOUT).toInt()

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
                    val testScenariosAndEndpointsPairList = contractPaths.split(",").map {
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
                    val tests: List<ContractTest> = testScenariosAndEndpointsPairList.flatMap { it.first }
                    val endpoints: List<Endpoint> = testScenariosAndEndpointsPairList.flatMap { it.second }
                    Pair(tests, endpoints)
                }
                else -> {
                    val configFile = configFile

                    exitIfDoesNotExist("config file", configFile)

                    createIfDoesNotExist(workingDirectory.path)

                    specmaticConfigJson = getSpecmaticJson()

                    val contractFilePaths = contractTestPathsFrom(configFile, workingDirectory.path)

                    val testScenariosAndEndpointsPairList = contractFilePaths.map { loadTestScenarios(it.path, "", "", testConfig, it.provider, it.repository, it.branch, it.specificationPath, specmaticConfigJson?.security, filterName, filterNotName) }

                    val tests: List<ContractTest> = testScenariosAndEndpointsPairList.flatMap { it.first }

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

        var checkedAPIs = false
        totalTestCount = testScenarios.size

        logger.log("Executing $totalTestCount tests")

        return testScenarios.map { testScenario ->
            DynamicTest.dynamicTest(testScenario.testDescription()) {
                threads.add(Thread.currentThread().name)

                if(!checkedAPIs) {
                    checkedAPIs = true

                    try {
                        queryActuator()
                    } catch(exception: Throwable) {
                        logger.log(exception, "Failed to query actuator with error")
                    }
                }

                lateinit var testResult: Pair<Result, HttpResponse?>

                try {
                    val protocol = System.getProperty("protocol") ?: "http"
                    val testBaseURL = System.getProperty(TEST_BASE_URL)
                        ?: "$protocol://${System.getProperty(HOST)}:${System.getProperty(PORT)}"
                    testResult = testScenario.runTest(testBaseURL, timeout)
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

                } catch(e: Throwable) {
                    throw e
                }
                finally {
                    val (result, response) = testResult
                    openApiCoverageReportInput.addTestReportRecords(testScenario.testResultRecord(result, response))
                }
            }
        }.toList()
    }

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
        filterNotName: String?
    ): Pair<List<ContractTest>, List<Endpoint>> {
        if(hasOpenApiFileExtension(path) && !isOpenAPI(path))
            return Pair(emptyList(), emptyList())

        val contractFile = File(path)
        val feature = parseContractFileToFeature(contractFile.path, CommandHook(HookName.test_load_contract), sourceProvider, sourceRepository, sourceRepositoryBranch, specificationPath, securityConfiguration).copy(testVariables = config.variables, testBaseURLs = config.baseURLs)
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

        val tests: List<ContractTest> = feature
            .copy(scenarios = selectTestsToRun(feature.scenarios, filterName, filterNotName) { it.testDescription() })
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

    private fun getSpecmaticJson(): SpecmaticConfigJson? {
        return try {
            loadSpecmaticJsonConfig(configFile)
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
    testScenarios: List<T>,
    filterName: String? = null,
    filterNotName: String? = null,
    getTestDescription: (T) -> String
): List<T> {
    val filteredByName = if (!filterName.isNullOrBlank()) {
        val filterNames = filterName.split(",").map { it.trim() }

        testScenarios.filter { test ->
            filterNames.any { getTestDescription(test).contains(it) }
        }
    } else
        testScenarios

    val filteredByNotName: List<T> = if(!filterNotName.isNullOrBlank()) {
        val filterNotNames = filterNotName.split(",").map { it.trim() }

        filteredByName.filterNot { test ->
            filterNotNames.any { getTestDescription(test).contains(it) }
        }
    } else
        filteredByName

    return filteredByNotName
}
