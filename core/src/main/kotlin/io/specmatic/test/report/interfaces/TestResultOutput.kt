package io.specmatic.test.report.interfaces

import io.specmatic.core.log.CurrentDate

interface TestResultOutput {
    val configFilePath: String
    val testResultRecords: List<TestResultRecord>
    val testStartTime: CurrentDate
    val testEndTime: CurrentDate
}