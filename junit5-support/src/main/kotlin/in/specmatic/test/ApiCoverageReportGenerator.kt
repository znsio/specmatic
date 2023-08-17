package `in`.specmatic.test

import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.TestResult
import kotlin.math.min
import kotlin.math.roundToInt

class ApiCoverageReportGenerator(
    private val testReportRecords: List<TestResultRecord>,
    private val applicationAPIs: List<API>,
    private val excludedAPIs: List<API> = emptyList()
) {

    fun generate(): APICoverageReport {

        val testReportRecordsIncludingMissingAPIs = testReportRecords.toMutableList()
        applicationAPIs.forEach { api ->
            if (testReportRecords.none { it.path == api.path && it.method == api.method } && excludedAPIs.none { it.path == api.path }) {
                testReportRecordsIncludingMissingAPIs.add(TestResultRecord(api.path, api.method, 0, TestResult.Skipped))
            }
        }

        val recordsWithFixedURLs = testReportRecordsIncludingMissingAPIs.map {
            it.copy(path = convertPathParameterStyle(it.path))
        }

        val coveredAPIs = recordsWithFixedURLs.groupBy {
            "${it.path}-${it.method}-${it.responseStatus}"
        }.let { sortedRecords: Map<String, List<TestResultRecord>> ->
            sortedRecords.keys.sorted().map { key ->
                sortedRecords.getValue(key)
            }
        }.flatten()

        // Creates a structure which looks like this:
        // mutableMapOf<Path, MutableMap<Method, MutableMap<ResponseStatus, MutableList<TestResultRecord>>>>
        val groupedAPIs = coveredAPIs.groupBy { it.path }
            .mapValues { (_, pathResults) ->
                pathResults.groupBy { it.method }
                    .mapValues { (_, methodResults) ->
                        methodResults.groupBy { it.responseStatus }
                            .mapValues { (_, responseResults) ->
                                responseResults.toMutableList()
                            }.toMutableMap()
                    }.toMutableMap()
            }.toMutableMap()


        val coveredAPIRows: MutableList<APICoverageRow> = mutableListOf()

        groupedAPIs.forEach { (route, methodMap) ->
            val routeAPIRows: MutableList<APICoverageRow> = mutableListOf()
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
                                count = testResults.count{it.result != TestResult.Skipped}.toString(),
                                coveragePercentage = 0
                            )
                        )
                    }
                }
            }
            coveredAPIRows.addAll(routeAPIRows)
        }

        val testedAPIs = testReportRecords.map { "${it.method}-${it.path}" }

        val missedAPIs = applicationAPIs.minus(excludedAPIs.toSet()).filter {
            "${it.method}-${it.path}" !in testedAPIs
        }

        val missedAPIRows = missedAPIs.map { missedAPI: API ->
            APICoverageRow(missedAPI.method, missedAPI.path, "", "")
        }

        return APICoverageReport(coveredAPIRows, missedAPIRows)
    }

    private fun createTopLevelApiCoverageRow(
        route: String,
        methodMap: MutableMap<String, MutableMap<Int, MutableList<TestResultRecord>>>,
    ): APICoverageRow {
        val method = methodMap.keys.first()
        val responseStatus = methodMap[method]?.keys?.first()
        val count = methodMap[method]?.get(responseStatus)?.count { it.result != TestResult.Skipped }

        val totalMethodResponseCodeCount = methodMap.values.sumOf { it.keys.size }
        var totalMethodResponseCodeExecutedWithExamples = 0
        methodMap.forEach { (_, responses) ->
            responses.forEach { (_, testResults) ->
                val nonSkippedTestsCount = min(testResults.count { it.result != TestResult.Skipped }, 1)
                totalMethodResponseCodeExecutedWithExamples += nonSkippedTestsCount
            }
        }

        val coveragePercentage =
            ((totalMethodResponseCodeExecutedWithExamples.toFloat() / totalMethodResponseCodeCount.toFloat()) * 100).roundToInt()
        return APICoverageRow(
            method,
            route,
            responseStatus!!,
            count!!,
            coveragePercentage
        )
    }
}

