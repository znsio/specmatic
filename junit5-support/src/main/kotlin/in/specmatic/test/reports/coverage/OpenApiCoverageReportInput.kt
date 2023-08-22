package `in`.specmatic.test.reports.coverage

import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.TestResult
import `in`.specmatic.test.API
import `in`.specmatic.test.TestResultRecord
import kotlin.math.min
import kotlin.math.roundToInt

class OpenApiCoverageReportInput(
    private val testResultRecords: MutableList<TestResultRecord> = mutableListOf(),
    private val applicationAPIs: MutableList<API> = mutableListOf(),
    private val excludedAPIs: MutableList<String> = mutableListOf()
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

    fun generate(): OpenAPICoverageReport {
        // If we get a failed test whose path and method are not found in the actuator, it means that the operation exists in the spec, but has not been implemented.
        val notImplementedTests =
            testResultRecords.filter { it.result == TestResult.Failed && applicationAPIs.none { api -> api.path == it.path && api.method == it.method } }
        val testResults = testResultRecords.minus(notImplementedTests.toSet())
            .plus(notImplementedTests.map { it.copy(result = TestResult.NotImplemented) })

        var allAPITests = addTestResultsForMissingEndpoints(testResults)
        allAPITests = sortByPathMethodResponseStatus(allAPITests)

        // Creates a structure which looks like this:
        // mutableMapOf<Path, MutableMap<Method, MutableMap<ResponseStatus, MutableList<TestResultRecord>>>>
        val apiTestsGrouped = allAPITests.groupBy { it.path }
            .mapValues { (_, pathResults) ->
                pathResults.groupBy { it.method }
                    .mapValues { (_, methodResults) ->
                        methodResults.groupBy { it.responseStatus }
                            .mapValues { (_, responseResults) ->
                                responseResults.toMutableList()
                            }.toMutableMap()
                    }.toMutableMap()
            }.toMutableMap()


        val apiCoverageRows: MutableList<OpenApiCoverageRow> = mutableListOf()

        apiTestsGrouped.forEach { (route, methodMap) ->
            val routeAPIRows: MutableList<OpenApiCoverageRow> = mutableListOf()
            val topLevelCoverageRow = createTopLevelApiCoverageRow(route, methodMap)
            methodMap.forEach { (method, responseCodeMap) ->
                responseCodeMap.forEach { (responseStatus, testResults) ->
                    if (routeAPIRows.isEmpty()) {
                        routeAPIRows.add(topLevelCoverageRow)
                    } else {
                        val lastCoverageRow = routeAPIRows.last()
                        val rowMethod = if (method != lastCoverageRow.method) method else ""
                        routeAPIRows.add(
                            topLevelCoverageRow.copy(
                                method = rowMethod,
                                path ="",
                                responseStatus = responseStatus.toString(),
                                count = testResults.count{it.includeForCoverage}.toString(),
                                coveragePercentage = 0
                            )
                        )
                    }
                }
            }
            apiCoverageRows.addAll(routeAPIRows)
        }

        val totalAPICount = apiTestsGrouped.keys.size
        val missedAPICount = allAPITests.groupBy { it.path }.filter { pathMap -> pathMap.value.any { it.result == TestResult.Skipped } }.size
        val notImplementedAPICount = allAPITests.groupBy { it.path }.filter { pathMap -> pathMap.value.any { it.result == TestResult.NotImplemented } }.size

        return OpenAPICoverageReport(apiCoverageRows, totalAPICount, missedAPICount, notImplementedAPICount)
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
        applicationAPIs.forEach { api ->
            if (testResults.none { it.path == api.path && it.method == api.method } && excludedAPIs.none { it == api.path }) {
                testReportRecordsIncludingMissingAPIs.add(TestResultRecord(api.path, api.method, 0, TestResult.Skipped))
            }
        }
        return testReportRecordsIncludingMissingAPIs
    }

    private fun createTopLevelApiCoverageRow(
        route: String,
        methodMap: MutableMap<String, MutableMap<Int, MutableList<TestResultRecord>>>,
    ): OpenApiCoverageRow {
        val method = methodMap.keys.first()
        val responseStatus = methodMap[method]?.keys?.first()
        val count = methodMap[method]?.get(responseStatus)?.count { it.includeForCoverage }

        val totalMethodResponseCodeCount = methodMap.values.sumOf { it.keys.size }
        var totalMethodResponseCodeCoveredCount = 0
        methodMap.forEach { (_, responses) ->
            responses.forEach { (_, testResults) ->
                val increment = min(testResults.count { it.includeForCoverage }, 1)
                totalMethodResponseCodeCoveredCount += increment
            }
        }

        val coveragePercentage =
            ((totalMethodResponseCodeCoveredCount.toFloat() / totalMethodResponseCodeCount.toFloat()) * 100).roundToInt()
        return OpenApiCoverageRow(
            method,
            route,
            responseStatus!!,
            count!!,
            coveragePercentage
        )
    }

}