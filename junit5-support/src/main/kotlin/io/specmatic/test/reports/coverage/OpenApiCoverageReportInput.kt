package io.specmatic.test.reports.coverage

import io.specmatic.conversions.SERVICE_TYPE_HTTP
import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.TestResult
import io.specmatic.test.API
import io.specmatic.test.TestResultRecord
import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.reports.coverage.console.Remarks
import io.specmatic.test.reports.coverage.json.OpenApiCoverageJsonReport
import kotlin.math.min
import kotlin.math.roundToInt

class OpenApiCoverageReportInput(
    private var configFilePath:String,
    internal val testResultRecords: MutableList<TestResultRecord> = mutableListOf(),
    private val applicationAPIs: MutableList<API> = mutableListOf(),
    private val excludedAPIs: MutableList<String> = mutableListOf(),
    private val allEndpoints: MutableList<Endpoint> = mutableListOf(),
    internal var endpointsAPISet: Boolean = false,
    internal var groupedTestResultRecords: MutableMap<String, MutableMap<String, MutableMap<Int, MutableList<TestResultRecord>>>> = mutableMapOf(),
    internal var apiCoverageRows: MutableList<OpenApiCoverageConsoleRow> = mutableListOf()
) {
    fun addTestReportRecords(testResultRecord: TestResultRecord) {
        testResultRecords.add(testResultRecord)
    }

    fun addAPIs(apis: List<API>) {
        applicationAPIs.addAll(apis)
    }

    fun addExcludedAPIs(apis: List<String>){
        excludedAPIs.addAll(apis)
    }

    fun addEndpoints(endpoints: List<Endpoint>) {
        allEndpoints.addAll(endpoints)
    }

    fun setEndpointsAPIFlag(isSet: Boolean) {
        endpointsAPISet = isSet
    }

    fun generate(): OpenAPICoverageConsoleReport {
        val testResults = testResultRecords.filter { testResult -> excludedAPIs.none { it == testResult.path } }
        val testResultsWithNotImplementedEndpoints = identifyFailedTestsDueToUnimplementedEndpointsAddMissingTests(testResults)
        var allTests = addTestResultsForMissingEndpoints(testResultsWithNotImplementedEndpoints)
        allTests = addTestResultsForTestsNotGeneratedBySpecmatic(allTests, allEndpoints)
        allTests = identifyWipTestsAndUpdateResult(allTests)
        allTests = checkForInvalidTestsAndUpdateResult(allTests)
        allTests = sortByPathMethodResponseStatus(allTests)

        groupedTestResultRecords = groupTestsByPathMethodAndResponseStatus(allTests)
        groupedTestResultRecords.forEach { (route, methodMap) ->
            val routeAPIRows: MutableList<OpenApiCoverageConsoleRow> = mutableListOf()
            val topLevelCoverageRow = createTopLevelApiCoverageRow(route, methodMap)
            methodMap.forEach { (method, responseCodeMap) ->
                responseCodeMap.forEach { (responseStatus, testResults) ->
                    if (routeAPIRows.isEmpty()) {
                        routeAPIRows.add(topLevelCoverageRow)
                    } else {
                        val methodExists = routeAPIRows.any { it.method == method }
                        routeAPIRows.add(
                            topLevelCoverageRow.copy(
                                method = method,
                                showMethod = !methodExists,
                                showPath = false,
                                responseStatus = responseStatus.toString(),
                                count = testResults.count{it.isExercised}.toString(),
                                remarks = Remarks.resolve(testResults),
                            )
                        )
                    }
                }
            }
            apiCoverageRows.addAll(routeAPIRows)
        }

        val totalAPICount = groupedTestResultRecords.keys.size
        val testsGroupedByPath = allTests.groupBy { it.path }
        val skippedAndMissingInSpecTestResults = setOf(TestResult.NotCovered, TestResult.MissingInSpec)

        val missedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.all { it.result in skippedAndMissingInSpecTestResults }
        }

        val notImplementedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.all { it.result == TestResult.NotImplemented }
        }

        val partiallyMissedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.any { it.result in skippedAndMissingInSpecTestResults } && tests.any { it.result !in skippedAndMissingInSpecTestResults }
        }

        val partiallyNotImplementedAPICount = testsGroupedByPath.count { (_, tests) ->
            tests.any { it.result == TestResult.NotImplemented } && tests.any { it.result != TestResult.NotImplemented }
        }

        return OpenAPICoverageConsoleReport(apiCoverageRows, totalAPICount, missedAPICount, notImplementedAPICount, partiallyMissedAPICount, partiallyNotImplementedAPICount)
    }

    private fun addTestResultsForTestsNotGeneratedBySpecmatic(allTests: List<TestResultRecord>, allEndpoints: List<Endpoint>): List<TestResultRecord> {
        val endpointsWithoutTests =
            allEndpoints.filter { endpoint ->
                allTests.none { it.path == endpoint.path && it.method == endpoint.method && it.responseStatus == endpoint.responseStatus }
                        && excludedAPIs.none { it == endpoint.path }
            }
        return allTests.plus(
            endpointsWithoutTests.map { endpoint ->  TestResultRecord(
                endpoint.path,
                endpoint.method,
                endpoint.responseStatus,
                TestResult.NotCovered,
                endpoint.sourceProvider,
                endpoint.sourceRepository,
                endpoint.sourceRepositoryBranch,
                endpoint.specification,
                endpoint.serviceType
            ) }
        )
    }

    fun generateJsonReport(): OpenApiCoverageJsonReport {
        val testResults = testResultRecords.filter { testResult -> excludedAPIs.none { it == testResult.path } }
        val testResultsWithNotImplementedEndpoints = identifyFailedTestsDueToUnimplementedEndpointsAddMissingTests(testResults)
        val allTests = addTestResultsForMissingEndpoints(testResultsWithNotImplementedEndpoints)
        return OpenApiCoverageJsonReport(configFilePath, allTests)
    }

    private fun groupTestsByPathMethodAndResponseStatus(allAPITests: List<TestResultRecord>): MutableMap<String, MutableMap<String, MutableMap<Int, MutableList<TestResultRecord>>>> {
        return allAPITests.groupBy { it.path }
            .mapValues { (_, pathResults) ->
                pathResults.groupBy { it.method }
                    .mapValues { (_, methodResults) ->
                        methodResults.groupBy { it.responseStatus }
                            .mapValues { (_, responseResults) ->
                                responseResults.toMutableList()
                            }.toMutableMap()
                    }.toMutableMap()
            }.toMutableMap()
    }

    private fun sortByPathMethodResponseStatus(testResultRecordList: List<TestResultRecord>): List<TestResultRecord> {
        val recordsWithFixedURLs = testResultRecordList.map {
            it.copy(path = convertPathParameterStyle(it.path))
        }
        return recordsWithFixedURLs.groupBy {
            "${it.path}-${it.method}-${it.responseStatus}"
        }.let { sortedRecords: Map<String, List<TestResultRecord>> ->
            sortedRecords.keys.sorted().map { key ->
                sortedRecords.getValue(key)
            }
        }.flatten()
    }

    private fun addTestResultsForMissingEndpoints(testResults: List<TestResultRecord>): List<TestResultRecord> {
        val testReportRecordsIncludingMissingAPIs = testResults.toMutableList()
        if(endpointsAPISet) {
            applicationAPIs.forEach { api ->
                if (allEndpoints.none { it.path == api.path && it.method == api.method } && excludedAPIs.none { it == api.path }) {
                    testReportRecordsIncludingMissingAPIs.add(
                        TestResultRecord(
                            api.path,
                            api.method,
                            0,
                            TestResult.MissingInSpec,
                            serviceType = SERVICE_TYPE_HTTP
                        )
                    )
                }
            }
        }
        return testReportRecordsIncludingMissingAPIs
    }

    private fun createTopLevelApiCoverageRow(
        route: String,
        methodMap: MutableMap<String, MutableMap<Int, MutableList<TestResultRecord>>>,
    ): OpenApiCoverageConsoleRow {
        val method = methodMap.keys.first()
        val responseStatus = methodMap[method]?.keys?.first()
        val remarks = Remarks.resolve(methodMap[method]?.get(responseStatus)!!)
        val exercisedCount = methodMap[method]?.get(responseStatus)?.count { it.isExercised }

        val totalMethodResponseCodeCount = methodMap.values.sumOf { it.keys.size }
        var totalMethodResponseCodeCoveredCount = 0
        methodMap.forEach { (_, responses) ->
            responses.forEach { (_, testResults) ->
                val increment = min(testResults.count { it.isCovered }, 1)
                totalMethodResponseCodeCoveredCount += increment
            }
        }

        val coveragePercentage =
            ((totalMethodResponseCodeCoveredCount.toFloat() / totalMethodResponseCodeCount.toFloat()) * 100).roundToInt()
        return OpenApiCoverageConsoleRow(
            method,
            route,
            responseStatus!!,
            exercisedCount!!,
            coveragePercentage,
            remarks
        )
    }

    private fun identifyFailedTestsDueToUnimplementedEndpointsAddMissingTests(testResults: List<TestResultRecord>): List<TestResultRecord> {
        val notImplementedAndMissingTests = mutableListOf<TestResultRecord>()
        val failedTestResults = testResults.filter { it.result == TestResult.Failed }

        for (failedTestResult in failedTestResults) {

            val pathHasErrorResponse = allEndpoints.any {
                it.path == failedTestResult.path && it.method == failedTestResult.method && it.responseStatus == failedTestResult.actualResponseStatus
            }

            if (!failedTestResult.isConnectionRefused() && !pathHasErrorResponse) {
                notImplementedAndMissingTests.add(
                    failedTestResult.copy(
                        responseStatus = failedTestResult.actualResponseStatus,
                        result = TestResult.MissingInSpec,
                        actualResponseStatus = failedTestResult.actualResponseStatus
                    )
                )
            }

            if (!endpointsAPISet) {
                notImplementedAndMissingTests.add(failedTestResult)
                continue
            }

            val isInApplicationAPI = applicationAPIs.any { api -> api.path == failedTestResult.path && api.method == failedTestResult.method }
            notImplementedAndMissingTests.add(failedTestResult.copy(result = if (isInApplicationAPI) TestResult.Failed else TestResult.NotImplemented))
        }

        return testResults.minus(failedTestResults.toSet()).plus(notImplementedAndMissingTests)
    }

    private fun checkForInvalidTestsAndUpdateResult(allTests: List<TestResultRecord>): List<TestResultRecord> {
        val invalidTestResults = allTests.filterNot(::isTestResultValid)
        val updatedInvalidTestResults = invalidTestResults.map { it.copy( isValid = false ) }

        return allTests.minus(invalidTestResults.toSet()).plus(updatedInvalidTestResults)
    }

    private fun isTestResultValid(testResultRecord: TestResultRecord): Boolean {
        val paramRegex = Regex("\\{.+}")
        val isPathWithParams = paramRegex.find(testResultRecord.path) != null
        if (isPathWithParams) return true

        return when (testResultRecord.responseStatus) {
            404 -> false
            else -> true
        }
    }

    private fun identifyWipTestsAndUpdateResult(testResults: List<TestResultRecord>): List<TestResultRecord> {
        val wipTestResults = testResults.filter { it.scenarioResult?.scenario?.ignoreFailure == true }
        val updatedWipTestResults = wipTestResults.map { it.copy(isWip = true) }
        return testResults.minus(wipTestResults.toSet()).plus(updatedWipTestResults)
    }
}

data class CoverageGroupKey(
    val sourceProvider: String?,
    val sourceRepository: String?,
    val sourceRepositoryBranch: String?,
    val specification: String?,
    val serviceType: String?
)

data class Endpoint(
    val path: String,
    val method: String,
    val responseStatus: Int,
    val sourceProvider: String? = null,
    val sourceRepository: String? = null,
    val sourceRepositoryBranch: String? = null,
    val specification: String? = null,
    val serviceType: String? = null
)