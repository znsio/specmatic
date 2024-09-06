package io.specmatic.test.report.interfaces

import io.specmatic.core.log.CurrentDate
import io.specmatic.test.report.ResultStatistics

interface ReportInput {
    val configFilePath: String
    val coverageRows: List<CoverageRow>
    val testResultRecords: List<TestResultRecord>
    val testsStartTime: CurrentDate
    val testsEndTime: CurrentDate
    val statistics: ResultStatistics

    fun isActuatorEnabled(): Boolean
    fun getHostAndPort(): Pair<String, String>
    fun getTotalDuration(): Long
    fun totalCoveragePercentage(): Int
}