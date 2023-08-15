package `in`.specmatic.test

import `in`.specmatic.conversions.convertPathParameterStyle

class ApiCoverageReportGenerator(
    private val testReportRecords: List<TestResultRecord>,
    private val applicationAPIs: List<API>,
    private val excludedAPIs: List<API> = emptyList()
) {

    fun generate(): APICoverageReport {

        val recordsWithFixedURLs = testReportRecords.map {
            it.copy(path = convertPathParameterStyle(it.path))
        }

        val coveredAPIs = recordsWithFixedURLs.groupBy {
            "${it.path}-${it.method}-${it.responseStatus}"
        }.let { sortedRecords: Map<String, List<TestResultRecord>> ->
            sortedRecords.keys.sorted().map { key ->
                sortedRecords.getValue(key)
            }
        }.flatten()

        val groupedAPIs = mutableMapOf<String, MutableMap<String, MutableMap<Int, MutableList<TestResultRecord>>>>()
        for (result in coveredAPIs) {
            groupedAPIs.getOrPut(result.path) { mutableMapOf() }
                .getOrPut(result.method) { mutableMapOf() }
                .getOrPut(result.responseStatus) { mutableListOf() }
                .add(result)
        }
        val coveredAPIRows:MutableList<APICoverageRow> = mutableListOf()

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
                                count = testResults.count().toString(),
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
            APICoverageRow(missedAPI.method, missedAPI.path, "", "", CoverageStatus.Missed)
        }

        return APICoverageReport(coveredAPIRows, missedAPIRows)
    }

    private fun createTopLevelApiCoverageRow(
        route: String,
        methodMap: MutableMap<String, MutableMap<Int, MutableList<TestResultRecord>>>,
    ): APICoverageRow {
        val method = methodMap.keys.first()
        val responseCode = methodMap[method]?.keys?.first()
        val count =  methodMap[method]?.get(responseCode)?.size
        return APICoverageRow(
            method,
            route,
            responseCode!!,
            count!!,
            CoverageStatus.Covered
        )
    }
}