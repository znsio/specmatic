package `in`.specmatic.test.reports.coverage

import `in`.specmatic.test.API
import `in`.specmatic.test.TestResultRecord

class ApiCoverageInput(
    val testResultRecords: MutableList<TestResultRecord> = mutableListOf(),
    val applicationAPIs: MutableList<API> = mutableListOf(),
    val excludedAPIs: List<String> = mutableListOf()
) {
    fun addTestReportRecords(testResultRecord: TestResultRecord) {
        testResultRecords.add(testResultRecord)
    }

    fun addAPIs(apis: List<API>) {
        applicationAPIs.addAll(apis)
    }
}