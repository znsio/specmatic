package `in`.specmatic.test.reports.coverage

import `in`.specmatic.conversions.SERVICE_TYPE_HTTP
import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.TestResult
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.test.API
import `in`.specmatic.test.TestResultRecord
import `in`.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import `in`.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import `in`.specmatic.test.reports.coverage.console.Remarks
import `in`.specmatic.test.reports.coverage.json.OpenApiCoverageJsonReport
import `in`.specmatic.test.reports.coverage.json.OpenApiCoverageJsonRow
import `in`.specmatic.test.reports.coverage.json.OpenApiCoverageOperation
import kotlin.math.min
import kotlin.math.roundToInt

class OpenApiCoverageReportInput(
    private var configFilePath:String,
    private val testResultRecords: MutableList<TestResultRecord> = mutableListOf(),
    private val applicationAPIs: MutableList<API> = mutableListOf(),
    private val excludedAPIs: MutableList<String> = mutableListOf(),
    private val allEndpoints: MutableList<Endpoint> = mutableListOf(),
    private var endpointsAPISet: Boolean = false
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
        val testResultsWithNotImplementedEndpoints = identifyTestsThatFailedBecauseOfEndpointsThatWereNotImplemented(testResults)
        var allTests = addTestResultsForMissingEndpoints(testResultsWithNotImplementedEndpoints)
        allTests = addTestResultsForTestsNotGeneratedBySpecmatic(allTests, allEndpoints)
        allTests = sortByPathMethodResponseStatus(allTests)

        val apiTestsGrouped = groupTestsByPathMethodAndResponseStatus(allTests)
        val apiCoverageRows: MutableList<OpenApiCoverageConsoleRow> = mutableListOf()
        apiTestsGrouped.forEach { (route, methodMap) ->
            val routeAPIRows: MutableList<OpenApiCoverageConsoleRow> = mutableListOf()
            val topLevelCoverageRow = createTopLevelApiCoverageRow(route, methodMap)
            methodMap.forEach { (method, responseCodeMap) ->
                responseCodeMap.forEach { (responseStatus, testResults) ->
                    if (routeAPIRows.isEmpty()) {
                        routeAPIRows.add(topLevelCoverageRow)
                    } else {
                        val rowMethod = if (routeAPIRows.none { it.method == method }) method else ""
                        routeAPIRows.add(
                            topLevelCoverageRow.copy(
                                method = rowMethod,
                                path ="",
                                responseStatus = responseStatus.toString(),
                                count = testResults.count{it.isExercised}.toString(),
                                coveragePercentage = 0,
                                remarks = getRemarks(testResults)
                            )
                        )
                    }
                }
            }
            apiCoverageRows.addAll(routeAPIRows)
        }

        val totalAPICount = apiTestsGrouped.keys.size

        val missedAPICount = allTests.groupBy { it.path }.filter { pathMap -> pathMap.value.all { it.result == TestResult.Skipped  } }.size

        val notImplementedAPICount = allTests.groupBy { it.path }.filter { pathMap -> pathMap.value.all { it.result in setOf(TestResult.NotImplemented,  TestResult.DidNotRun) } }.size

        val partiallyMissedAPICount = allTests.groupBy { it.path }
            .count { (_, tests) ->
                tests.any { it.result == TestResult.Skipped } && tests.any {it.result != TestResult.Skipped }
            }

        val partiallyNotImplementedAPICount = allTests.groupBy { it.path }
            .count { (_, tests) ->
                tests.any { it.result == TestResult.NotImplemented } && tests.any {it.result in setOf(TestResult.Success , TestResult.Skipped, TestResult.Failed) }
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
                TestResult.DidNotRun,
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
        val testResultsWithNotImplementedEndpoints = identifyTestsThatFailedBecauseOfEndpointsThatWereNotImplemented(testResults)
        val allTests = addTestResultsForMissingEndpoints(testResultsWithNotImplementedEndpoints)

        val openApiCoverageJsonRows = allTests.groupBy {
            CoverageGroupKey(it.sourceProvider, it.sourceRepository, it.sourceRepositoryBranch, it.specification, it.serviceType)
        }.map { (key, recordsOfGroup) ->
            OpenApiCoverageJsonRow(
                type = key.sourceProvider,
                repository = key.sourceRepository,
                branch = key.sourceRepositoryBranch,
                specification = key.specification,
                serviceType = key.serviceType,
                operations = recordsOfGroup.groupBy {
                    Triple(it.path, it.method, it.responseStatus)
                }.map { (operationGroup, operationRows) ->
                    OpenApiCoverageOperation(
                        path = operationGroup.first,
                        method = operationGroup.second,
                        responseCode = operationGroup.third,
                        count = operationRows.count{it.isExercised},
                        coverageStatus = getRemarks(operationRows).toString()
                    )
                }
            )
        }
        return OpenApiCoverageJsonReport(configFilePath, apiCoverage = openApiCoverageJsonRows)
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
                            TestResult.Skipped,
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
        val remarks = getRemarks(methodMap[method]?.get(responseStatus)!!)
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

    private fun getRemarks(testResultRecords: List<TestResultRecord>): Remarks {
        val exerciseCount = testResultRecords.count { it.isExercised }
        return when (exerciseCount == 0) {
            true -> {
                when (val result = testResultRecords.first().result) {
                    TestResult.Skipped -> Remarks.Missed
                    TestResult.DidNotRun -> Remarks.DidNotRun
                    else -> throw ContractException("Cannot determine remarks for unknown test result: $result")
                }
            }

            else -> {
                when(testResultRecords.first().result) {
                    TestResult.NotImplemented -> Remarks.NotImplemented
                    else -> Remarks.Covered
                }
            }
        }
    }

    private fun identifyTestsThatFailedBecauseOfEndpointsThatWereNotImplemented(testResults: List<TestResultRecord>): List<TestResultRecord> {
        val notImplementedTests =
            testResults.filter { it.result == TestResult.Failed && (endpointsAPISet && applicationAPIs.none { api -> api.path == it.path && api.method == it.method }) }
        return testResults.minus(notImplementedTests.toSet())
            .plus(notImplementedTests.map { it.copy(result = TestResult.NotImplemented) })
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