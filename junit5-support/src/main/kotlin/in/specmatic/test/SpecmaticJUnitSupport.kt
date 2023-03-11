package `in`.specmatic.test

import `in`.specmatic.core.*
import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.log.ignoreLog
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.Examples
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.opentest4j.TestAbortedException
import java.io.File

data class API(val method: String, val path: String)

data class CoveredAPIRow(val method: String, val path: String, val status: String, val count: Int) {
    constructor(method: String, path: String, status: Int, count: Int): this(method, path, status.toString(), count)

    fun toRowString(maxPathSize: Int): String {
        val pathFormat = "%${maxPathSize}s"
        val methodFormat = "%${"method".length}s"
        val statusFormat = "%${"status".length}s"
        val countFormat = "%${"count".length}s"

        return "| ${pathFormat.format(path)} | ${methodFormat.format(method)} | ${statusFormat.format(status)} | ${countFormat.format(count)} |"
    }
}

data class MissedAPIRow(val method: String, val path: String) {
    fun toRowString(maxPathSize: Int): String {
        val pathFormat = "%${maxPathSize}s"
        val methodFormat = "%${"method".length}s"

        return "| ${pathFormat.format(path)} | ${methodFormat.format(method)} |"
    }
}

class APICoverageReport(private val coveredAPIRows: List<CoveredAPIRow>, private val missedAPIRows: List<MissedAPIRow>) {
    fun toLogString(): String {
        val maxPathSize: Int = coveredAPIRows.map { it.path.length }.plus(missedAPIRows.map { it.path.length }).max()

        val pathFormat = "%${maxPathSize}s"
        val methodFormat = "%${"method".length}s"
        val statusFormat = "%${"status".length}s"
        val countFormat = "%${"count".length}s"

        val tableHeader = "| ${pathFormat.format("path")} | ${methodFormat.format("method")} | ${statusFormat.format("status")} | ${countFormat.format("count")} |"
        val headerSeparator ="|-${"-".repeat(maxPathSize)}-|-${methodFormat.format("------")}-|-${statusFormat.format("-----")}-|-${countFormat.format("-----")}-|"

        val headerTitleSize = tableHeader.length - 4
        val tableTitle = "| ${"%-${headerTitleSize}s".format("Covered APIs")} |"
        val titleSeparator = "|-${"-".repeat(headerTitleSize)}-|"

        val coveredCount = coveredAPIRows.map { it.path }.distinct().size
        val uncoveredCount = missedAPIRows.map { it.path }.distinct().size
        val total = coveredCount + uncoveredCount

        val summary = "$coveredCount / $total APIs covered"
        val summaryRowFormatter = "%-${headerTitleSize}s"
        val summaryRow = "| ${summaryRowFormatter.format(summary)} |"

        val coveredRowTableString = listOf(titleSeparator, tableTitle, titleSeparator, tableHeader, headerSeparator).plus(coveredAPIRows.map { it.toRowString(maxPathSize) }).plus(titleSeparator).plus(summaryRow).plus(titleSeparator).joinToString(System.lineSeparator())

        if(missedAPIRows.isNotEmpty()) {
            val uncoveredTableHeader = "| ${pathFormat.format("path")} | ${methodFormat.format("method")} |"
            val uncoveredHeaderSeparator ="|-${"-".repeat(maxPathSize)}-|-${methodFormat.format("------")}-|"

            val uncoveredHeaderTitleSize = uncoveredTableHeader.length - 4
            val uncoveredTableTitle = "| ${"%-${uncoveredHeaderTitleSize}s".format("Uncovered APIs")} |"
            val uncoveredTitleSeparator = "|-${"-".repeat(uncoveredHeaderTitleSize)}-|"

            val uncoveredSummary = "$uncoveredCount / $total APIs not covered"
            val uncoveredSummaryRowFormatter = "%-${uncoveredHeaderTitleSize}s"
            val uncoveredSummaryRow = "| ${uncoveredSummaryRowFormatter.format(uncoveredSummary)} |"

            val uncoveredRowTableString = listOf(uncoveredTitleSeparator, uncoveredTableTitle, uncoveredTitleSeparator, uncoveredTableHeader, uncoveredHeaderSeparator).plus(missedAPIRows.map { it.toRowString(maxPathSize) }).plus(uncoveredTitleSeparator).plus(uncoveredSummaryRow).plus(uncoveredTitleSeparator).joinToString(System.lineSeparator())

            return listOf(coveredRowTableString, uncoveredRowTableString).joinToString(System.lineSeparator().repeat(2))
        }

        return coveredRowTableString
    }
}

class TestReport(private val testReportRecords: MutableList<TestResultRecord> = mutableListOf(), private val applicationAPIs: MutableList<API> = mutableListOf()) {
    fun addTestReportRecords(testResultRecord: TestResultRecord) {
        testReportRecords.add(testResultRecord)
    }

    fun addAPIs(apis: List<API>) {
        applicationAPIs.addAll(apis)
    }

    fun printReport2() {
        logger.log("COVERAGE SUMMARY")
        logger.log("----------------")
        logger.newLine()

        val recordsWithFixedURLs = testReportRecords.map {
            it.copy(path = it.path.replace(Regex("""\((.*):.*\)"""), "{$1}"))
        }

        val coveredAPIRows = recordsWithFixedURLs.groupBy {
            "${it.path}-${it.method}-${it.responseStatus}"
        }.let { sortedRecords: Map<String, List<TestResultRecord>> ->
            sortedRecords.keys.sorted().map { key ->
                sortedRecords.getValue(key)
            }
        }.let { groupedRecords: List<List<TestResultRecord>> ->
            groupedRecords.fold(emptyList()) { acc: List<CoveredAPIRow>, record: List<TestResultRecord> ->
                val stat = record.first().let { CoveredAPIRow(it.method, it.path, it.responseStatus, record.size) }
                when(acc) {
                    emptyList<CoveredAPIRow>() -> listOf(stat)
                    else -> {
                        val checkedPath = if(stat.path == acc.lastOrNull { it.path.isNotEmpty() }?.path) stat.copy(path = "") else stat
                        val checkedMethod = if(checkedPath.method == acc.lastOrNull { it.method.isNotEmpty() }?.method) checkedPath.copy(method = "") else checkedPath

                        acc.plus(checkedMethod)
                    }
                }
            }
        }

        val testedAPIs = testReportRecords.map { "${it.method}-${it.path}" }

        val missedAPIs = applicationAPIs.filter {
            "${it.method}-${it.path}" !in testedAPIs
        }

        val missedAPIRows = missedAPIs.map { missedAPI: API ->
            MissedAPIRow(missedAPI.method, missedAPI.path)
        }

        logger.log(APICoverageReport(coveredAPIRows, missedAPIRows).toLogString())
    }

    fun printReport() {
        logger.log("API COVERAGE REPORT")
        logger.log("-------------------")
        logger.newLine()

        testReportRecords.map { it.path }.distinct().forEach { path ->
            val recordsForPath = testReportRecords.filter {
                it.path == path
            }

            val groups = recordsForPath.groupBy {
                Pair(it.method, it.responseStatus)
            }

            groups.keys.distinct().sortedBy { "${it.first}-${it.second}" }.forEach { key ->
                val records = groups.getValue(key)

                val (method, status) = key

                logger.log("$method $path")

                records.forEach {
                    logger.log("  $status: ${records.size}")
                }
            }

            logger.newLine()
        }

        val testedAPIs = testReportRecords.map { "${it.method}-${it.path}" }

        val missedAPIs = applicationAPIs.filter {
            "${it.method}-${it.path}" !in testedAPIs
        }

        if(missedAPIs.isNotEmpty()) {
            logger.log("Untested APIS:")

            missedAPIs.map {
                it.method + " " + it.path
            }.forEach {
                logger.log(it)
            }
        }
    }
}

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
        const val ENDPOINTS_API = "endpointsAPI"

        val testsNames = mutableListOf<String>()
        val partialSuccesses: MutableList<Result.Success> = mutableListOf()
        val testReport: TestReport = TestReport()

        @AfterAll
        @JvmStatic
        fun report() {
            testReport.printReport2()
        }

        fun queryActuator() {
            val endpointsAPI = System.getProperty(ENDPOINTS_API)

            if(endpointsAPI != null) {
                val request = HttpRequest("GET")

                val response = HttpClient(endpointsAPI, log = ignoreLog).execute(request)

                logger.debug(response.toLogString())

                val endpointData = response.body as JSONObjectValue
                val apis: List<API> = endpointData.getJSONObject("contexts").entries.flatMap {
                    val mappings: JSONArrayValue =
                        (it.value as JSONObjectValue).findFirstChildByPath("mappings.dispatcherServlets.dispatcherServlet") as JSONArrayValue
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

                testReport.addAPIs(apis)
            } else {
                logger.log("Endpoints API not found, cannot calculate actual coverage")
            }
        }
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
            testsNames.add("Load Error")
            logger.log(e)
            ResultAssert.assertThat(Result.Failure(exceptionCauseMessage(e))).isSuccess()
        })
    }

    @TestFactory
    fun contractTest(): Collection<DynamicTest> {
        val contractPaths = System.getProperty(CONTRACT_PATHS)
        val givenWorkingDirectory = System.getProperty(WORKING_DIRECTORY)
        val givenConfigFile = System.getProperty(CONFIG_FILE_NAME)
        val filterName: String? = System.getProperty(FILTER_NAME)

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
            val testScenarios = when {
                contractPaths != null -> {
                    contractPaths.split(",").flatMap { loadTestScenarios(it, suggestionsPath, suggestionsData, testConfig) }
                }
                else -> {
                    val configFile = givenConfigFile ?: globalConfigFileName

                    exitIfDoesNotExist("config file", configFile)

                    createIfDoesNotExist(workingDirectory.path)

                    val contractFilePaths = contractTestPathsFrom(configFile, workingDirectory.path).map { it.path }
                    contractFilePaths.flatMap { loadTestScenarios(it, "", "", testConfig) }
                }
            }

            if(filterName != null) {
                testScenarios.filter {
                    it.testDescription().contains(filterName)
                }
            } else
                testScenarios
        } catch(e: ContractException) {
            return loadExceptionAsTestError(e)
        } catch(e: Throwable) {
            return loadExceptionAsTestError(e)
        } finally {
            workingDirectory.delete()
        }

        val invoker = when(val testBaseURL = System.getProperty(TEST_BASE_URL)) {
            null -> TargetHostAndPort(System.getProperty(HOST), System.getProperty(PORT))
            else -> TargetBaseURL(testBaseURL)
        }

        var checkedAPIs = false

        return testScenarios.map { testScenario ->
            DynamicTest.dynamicTest(testScenario.testDescription()) {
                if(!checkedAPIs) {
                    checkedAPIs = true

                    try {
                        queryActuator()
                    } catch(exception: Throwable) {
                        logger.log(exception, "Failed to query actuator with error")
                    }
                }

                testsNames.add(testScenario.testDescription())

                val result: Result = invoker.execute(testScenario, timeout)

                testReport.addTestReportRecords(testScenario.testResultRecord(result))

                if(result is Result.Success && result.isPartialSuccess()) {
                    partialSuccesses.add(result)
                }

                when {
                    result.shouldBeIgnored() -> {
                        val message = "Test FAILED, ignoring since the scenario is tagged @WIP${System.lineSeparator()}${result.toReport().toText().prependIndent("  ")}"
                        throw TestAbortedException(message)
                    }
                    else -> ResultAssert.assertThat(result).isSuccess()
                }
            }
        }.toList()
    }

    private fun loadTestScenarios(
        path: String,
        suggestionsPath: String,
        suggestionsData: String,
        config: TestConfig
    ): List<ContractTest> {
        val contractFile = File(path)
        val feature = parseContractFileToFeature(contractFile.path).copy(testVariables = config.variables, testBaseURLs = config.baseURLs)

        val suggestions = when {
            suggestionsPath.isNotEmpty() -> suggestionsFromFile(suggestionsPath)
            suggestionsData.isNotEmpty() -> suggestionsFromCommandLine(suggestionsData)
            else -> emptyList()
        }

        return feature.generateContractTests(suggestions)
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
                        asJSONObjectValue(row, "Each value in the list of suggestions must be a json object containing column name as key and sample value as the value")
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
                null,
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

private fun asJSONObjectValue(value: Value, errorMessage: String): Map<String, Value> {
    if(value !is JSONObjectValue)
        throw ContractException(errorMessage)

    return value.jsonObject
}
