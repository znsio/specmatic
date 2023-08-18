package `in`.specmatic.test

import `in`.specmatic.core.log.logger
import `in`.specmatic.test.formatters.CoverageReportTextFormatter

class TestReport(private val testReportRecords: MutableList<TestResultRecord> = mutableListOf(), private val applicationAPIs: MutableList<API> = mutableListOf(), private val excludedAPIs: MutableList<String> = mutableListOf()) {
    fun addTestReportRecords(testResultRecord: TestResultRecord) {
        testReportRecords.add(testResultRecord)
    }

    fun addAPIs(apis: List<API>) {
        applicationAPIs.addAll(apis)
    }

    fun addExcludedAPIs(apis: List<String>) {
        excludedAPIs.addAll(apis)
    }

    fun printReport() {
        if(testReportRecords.isEmpty())
            return

        logger.newLine()
        val apiCoverageReport = ApiCoverageReportGenerator(testReportRecords, applicationAPIs, excludedAPIs).generate()
        val textFormatter = CoverageReportTextFormatter()
        logger.log(textFormatter.format(apiCoverageReport))
    }
}