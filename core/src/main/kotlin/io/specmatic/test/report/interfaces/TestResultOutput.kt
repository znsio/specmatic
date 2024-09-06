package io.specmatic.test.report.interfaces

import io.specmatic.core.log.CurrentDate

interface TestResultOutput<T : TestResultRecord> {
    val configFilePath: String
    val testResultRecords: List<T>
    val testStartTime: CurrentDate
    val testEndTime: CurrentDate
}