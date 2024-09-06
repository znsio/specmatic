package io.specmatic.test

import io.specmatic.core.log.CurrentDate
import io.specmatic.test.reports.coverage.Endpoint
import io.specmatic.test.reports.coverage.OpenApiTestResultOutput
import io.specmatic.test.report.ResultStatistics
import io.specmatic.test.reports.coverage.OpenApiTestResultTransformer
import io.specmatic.test.reports.coverage.OpenApiReportInput
import io.specmatic.test.reports.coverage.OpenApiCoverageRow
import org.assertj.core.api.Assertions.assertThat

fun generateCoverageReport(
    testResultRecords: List<OpenApiTestResultRecord>, allEndpoints: List<Endpoint>,
    applicationAPIS: List<API>? = null, excludedAPIs: List<String> = emptyList()
): OpenApiReportInput {
    val coverageReportInput = OpenApiTestResultOutput(
        configFilePath = "", testResultRecords = testResultRecords,
        allEndpoints = allEndpoints, testStartTime = CurrentDate(), testEndTime = CurrentDate(),
        applicationAPIs = emptyList(), endpointsAPISet = false, excludedAPIs = excludedAPIs
    )

    val updatedCoverageReportInput = when (applicationAPIS) {
        null -> coverageReportInput
        else -> coverageReportInput.copy(applicationAPIs = applicationAPIS, endpointsAPISet = true)
    }

    return OpenApiTestResultTransformer(updatedCoverageReportInput).toReportInput()
}

fun assertReportEquals(
    actualCoverageReport: OpenApiReportInput,
    expectedCoverageRows: List<OpenApiCoverageRow>,
    expectedStatistics: ResultStatistics
) {
    assertThat(actualCoverageReport).isEqualTo(
        OpenApiReportInput(
            configFilePath = "", coverageRows = expectedCoverageRows,
            testResultRecords = actualCoverageReport.testResultRecords, statistics = expectedStatistics,
            testsStartTime = actualCoverageReport.testsStartTime, testsEndTime = actualCoverageReport.testsEndTime,
            actuatorEnabled = actualCoverageReport.actuatorEnabled
        )
    )
}