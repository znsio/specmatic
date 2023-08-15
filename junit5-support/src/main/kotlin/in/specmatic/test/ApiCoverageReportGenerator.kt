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

        val coveredAPIRows = recordsWithFixedURLs.groupBy {
            "${it.path}-${it.method}-${it.responseStatus}"
        }.let { sortedRecords: Map<String, List<TestResultRecord>> ->
            sortedRecords.keys.sorted().map { key ->
                sortedRecords.getValue(key)
            }
        }.let { groupedRecords: List<List<TestResultRecord>> ->
            groupedRecords.fold(emptyList()) { acc: List<APICoverageRow>, record: List<TestResultRecord> ->
                val stat = record.first().let {
                    APICoverageRow(
                        it.method,
                        it.path,
                        it.responseStatus,
                        record.size,
                        CoverageStatus.Covered
                    )
                }

                when (acc) {
                    emptyList<APICoverageRow>() -> listOf(stat)
                    else -> {
                        val checkedPath =
                            if (stat.path == acc.lastOrNull { it.path.isNotEmpty() }?.path) stat.copy(path = "") else stat
                        val checkedMethod =
                            if (checkedPath.method == acc.lastOrNull { it.method.isNotEmpty() }?.method) checkedPath.copy(
                                method = ""
                            ) else checkedPath

                        acc.plus(checkedMethod)
                    }
                }
            }
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
}