package io.specmatic.test.reports.coverage.console

import io.specmatic.core.log.CurrentDate
import io.specmatic.core.utilities.Flags.Companion.getStringValue
import io.specmatic.test.SpecmaticJUnitSupport
import io.specmatic.test.TestInteractionsLog
import io.specmatic.test.TestResultRecord
import kotlin.math.roundToInt

data class OpenAPICoverageConsoleReport(
    val coverageRows: List<OpenApiCoverageConsoleRow>,
    val testResultRecords: List<TestResultRecord>,
    val testsStartTime: CurrentDate,
    val testsEndTime: CurrentDate,
    val totalEndpointsCount: Int,
    val missedEndpointsCount: Int,
    val notImplementedAPICount: Int,
    val partiallyMissedEndpointsCount: Int,
    val partiallyNotImplementedAPICount: Int
) {
    val totalCoveragePercentage: Int = calculateTotalCoveragePercentage()
    val httpLogMessages = TestInteractionsLog.testHttpLogMessages

    private fun calculateTotalCoveragePercentage(): Int {
        if (totalEndpointsCount == 0) return 0

        val totalCountOfEndpointsWithResponseStatuses = coverageRows.count()
        val totalCountOfCoveredEndpointsWithResponseStatuses = coverageRows.count { it.count.toInt() > 0 }

        return ((totalCountOfCoveredEndpointsWithResponseStatuses * 100) / totalCountOfEndpointsWithResponseStatuses).toDouble().roundToInt()
    }

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

    fun totalPaths(): Int = coverageRows.count { it.showPath }

    fun totalTestDuration(): Long = testsEndTime.toEpochMillis() - testsStartTime.toEpochMillis()

    fun getGroupedTestResultRecords(): Map<String, Map<String, Map<String, List<TestResultRecord>>>> {
        return testResultRecords.groupBy { it.path }.mapValues { serviceGroup ->
            serviceGroup.value.groupBy { it.method }.mapValues { rpcGroup ->
                rpcGroup.value.groupBy { it.responseStatus.toString() }
            }
        }
    }

    fun getGroupedCoverageRows(): Map<String, Map<String, Map<String, List<OpenApiCoverageConsoleRow>>>> {
        return coverageRows.groupBy { it.path }.mapValues { serviceGroup ->
            serviceGroup.value.groupBy { it.method }.mapValues { rpcGroup ->
                rpcGroup.value.groupBy { it.responseStatus }
            }
        }
    }

}