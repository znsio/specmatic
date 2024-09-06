package io.specmatic.test.reports.coverage

import io.specmatic.core.log.CurrentDate
import io.specmatic.core.log.HttpLogMessage
import io.specmatic.core.utilities.Flags.Companion.getStringValue
import io.specmatic.test.SpecmaticJUnitSupport
import io.specmatic.test.TestInteractionsLog
import io.specmatic.test.OpenApiTestResultRecord
import io.specmatic.test.report.ResultStatistics
import io.specmatic.test.report.interfaces.ReportInput
import kotlin.math.roundToInt

data class OpenApiReportInput (
    override val configFilePath: String,
    override val coverageRows: List<OpenApiCoverageRow>,
    override val testResultRecords: List<OpenApiTestResultRecord>,
    override val testsStartTime: CurrentDate,
    override val testsEndTime: CurrentDate,
    override val statistics: ResultStatistics,
    val actuatorEnabled: Boolean
): ReportInput {
    val httpLogMessages: List<HttpLogMessage> = TestInteractionsLog.testHttpLogMessages

    override fun getHostAndPort(): Pair<String, String> {
        httpLogMessages.ifEmpty {
            return Pair(getStringValue(SpecmaticJUnitSupport.HOST) ?: "unknown", getStringValue(SpecmaticJUnitSupport.PORT) ?: "unknown")
        }

        return try {
            httpLogMessages.first().targetServer.split("://").last().split(":").let { Pair(it[0], it[1]) }
        }  catch (e: Exception) {
            Pair("unknown", "unknown")
        }
    }
    override fun getTotalDuration(): Long = testsEndTime.toEpochMillis() - testsStartTime.toEpochMillis()
    override fun totalCoveragePercentage(): Int {
        if (statistics.totalEndpointsCount == 0) return 0

        val totalCountOfEndpointsWithResponseStatuses = coverageRows.count()
        val totalCountOfCoveredEndpointsWithResponseStatuses = coverageRows.count { it.exercisedCount > 0 }

        return ((totalCountOfCoveredEndpointsWithResponseStatuses * 100) / totalCountOfEndpointsWithResponseStatuses).toDouble().roundToInt()
    }
    override fun isActuatorEnabled(): Boolean = actuatorEnabled
}