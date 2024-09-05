package io.specmatic.test

import io.specmatic.core.log.CurrentDate
import io.specmatic.test.reports.coverage.Endpoint
import io.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import io.specmatic.test.reports.coverage.ResultStatistics
import io.specmatic.test.reports.coverage.TestResultsTransformer
import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import org.assertj.core.api.Assertions.assertThat

fun generateCoverageReport(
    testResultRecords: List<TestResultRecord>, allEndpoints: List<Endpoint>,
    applicationAPIS: List<API>? = null, excludedAPIs: List<String> = emptyList()
): OpenAPICoverageConsoleReport {
    val coverageReportInput = OpenApiCoverageReportInput(
        configFilePath = "", testResultRecords = testResultRecords,
        allEndpoints = allEndpoints, testStartTime = CurrentDate(), testEndTime = CurrentDate(),
        applicationAPIs = emptyList(), endpointsAPISet = false, excludedAPIs = excludedAPIs
    )

    val updatedCoverageReportInput = when (applicationAPIS) {
        null -> coverageReportInput
        else -> coverageReportInput.copy(applicationAPIs = applicationAPIS, endpointsAPISet = true)

    }

    return TestResultsTransformer(updatedCoverageReportInput).toCoverageReport()
}

fun assertReportEquals(
    actualCoverageReport: OpenAPICoverageConsoleReport,
    expectedCoverageRows: List<OpenApiCoverageConsoleRow>,
    expectedStatistics: ResultStatistics
) {
    assertThat(actualCoverageReport).isEqualTo(
        OpenAPICoverageConsoleReport(
            configFilePath = "", coverageRows = expectedCoverageRows,
            testResultRecords = actualCoverageReport.testResultRecords, statistics = expectedStatistics,
            testsStartTime = actualCoverageReport.testsStartTime, testsEndTime = actualCoverageReport.testsEndTime
        )
    )
}