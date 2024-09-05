package io.specmatic.test.reports.coverage.console

import io.specmatic.core.log.CurrentDate
import io.specmatic.core.log.HttpLogMessage
import io.specmatic.core.utilities.Flags.Companion.getStringValue
import io.specmatic.test.SpecmaticJUnitSupport
import io.specmatic.test.TestInteractionsLog
import io.specmatic.test.TestResultRecord
import io.specmatic.test.reports.coverage.ResultStatistics
import kotlin.math.roundToInt

data class OpenAPICoverageConsoleReport(
    val configFilePath: String,
    val coverageRows: List<OpenApiCoverageConsoleRow>,
    val testResultRecords: List<TestResultRecord>,
    val testsStartTime: CurrentDate,
    val testsEndTime: CurrentDate,
    val statistics: ResultStatistics
) {
    val totalPaths = statistics.totalEndpointsCount
    val totalCoveragePercentage: Int = calculateTotalCoveragePercentage()
    val httpLogMessages: List<HttpLogMessage> = TestInteractionsLog.testHttpLogMessages

    fun getHostAndPort(): Pair<String, String> {
        httpLogMessages.ifEmpty {
            return Pair(getStringValue(SpecmaticJUnitSupport.HOST) ?: "unknown", getStringValue(SpecmaticJUnitSupport.PORT) ?: "unknown")
        }

        return try {
            httpLogMessages.first().targetServer.split("://").last().split(":").let { Pair(it[0], it[1]) }
        }  catch (e: Exception) {
            Pair("unknown", "unknown")
        }
    }

    fun getTotalDuration(): Long = testsEndTime.toEpochMillis() - testsStartTime.toEpochMillis()

    fun getGroupedTestResultRecords(): Map<String, Map<String, Map<String, List<TestResultRecord>>>> {
        return testResultRecords.groupBy { it.path }.mapValues { pathGroup ->
            pathGroup.value.groupBy { it.method }.mapValues { methodGroup ->
                methodGroup.value.groupBy { it.responseStatus.toString() }
            }
        }
    }

    fun getGroupedCoverageRows(): Map<String, Map<String, Map<String, List<OpenApiCoverageConsoleRow>>>> {
        return coverageRows.groupBy { it.path }.mapValues { pathGroup ->
            pathGroup.value.groupBy { it.method }.mapValues { methodGroup ->
                methodGroup.value.groupBy { it.responseStatus }
            }
        }
    }

    private fun calculateTotalCoveragePercentage(): Int {
        if (statistics.totalEndpointsCount == 0) return 0

        val totalCountOfEndpointsWithResponseStatuses = coverageRows.count()
        val totalCountOfCoveredEndpointsWithResponseStatuses = coverageRows.count { it.count.toInt() > 0 }

        return ((totalCountOfCoveredEndpointsWithResponseStatuses * 100) / totalCountOfEndpointsWithResponseStatuses).toDouble().roundToInt()
    }

}