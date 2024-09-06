package io.specmatic.test

import io.specmatic.core.log.CurrentDate
import io.specmatic.test.report.ResultStatistics
import io.specmatic.test.reports.coverage.OpenApiReportInput
import io.specmatic.test.reports.coverage.OpenApiCoverageRow
import io.specmatic.test.report.Remarks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiCoverageConsoleReportTest {

    companion object {
        val utilReport = OpenApiReportInput(
            configFilePath = "",
            testResultRecords = emptyList(),
            coverageRows = emptyList(),
            testsStartTime = CurrentDate(),
            testsEndTime = CurrentDate(),
            statistics = ResultStatistics(
                0,0,0,0, 0
            ),
            actuatorEnabled = false
        )
    }

    @Test
    fun `test calculates total percentage based on number of exercised endpoints`() {
        val rows = listOf(
            OpenApiCoverageRow("GET", "/route1", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageRow("POST", "/route1", 200, 0, 0, Remarks.Missed),
            OpenApiCoverageRow("GET", "/route2", 200, 1, 25, Remarks.Covered),
            OpenApiCoverageRow("POST", "/route2", 200, 0, 0, Remarks.NotCovered),
            OpenApiCoverageRow("POST", "/route2", 400, 0, 0, Remarks.NotCovered),
            OpenApiCoverageRow("POST", "/route2", 500, 0, 0, Remarks.NotCovered),
            OpenApiCoverageRow("GET", "/route3", 200, 0, 0, Remarks.NotCovered),
        )

        val resultStatistics = ResultStatistics(3, 0, 1, 0, 0)
        val report = utilReport.copy(coverageRows =  rows, statistics =  resultStatistics)

        assertThat(report.totalCoveragePercentage()).isEqualTo(28)
    }

    @Test
    fun `should calculate overall coverage percentage based on exercised endpoints with WIP`() {
        val rows = listOf(
            OpenApiCoverageRow("GET", "/route1", 200, 2, 100, Remarks.Covered),
            OpenApiCoverageRow("GET", "/route1", 400, 2, 100, Remarks.Covered),
            OpenApiCoverageRow("GET", "/route1", 503, 1, 100, Remarks.Covered),
            OpenApiCoverageRow("GET", "/route2", 200, 2, 80, Remarks.Wip),
            OpenApiCoverageRow("GET", "/route2", 400, 2, 80, Remarks.Wip),
            OpenApiCoverageRow("POST", "/route2", 201, 1, 80, Remarks.Covered),
            OpenApiCoverageRow("POST", "/route2", 400, 6, 80, Remarks.Covered),
            OpenApiCoverageRow("POST", "/route2", 503, 0, 80, Remarks.NotCovered),
            OpenApiCoverageRow("POST", "/route3", 210, 12, 67, Remarks.Covered),
            OpenApiCoverageRow("POST", "/route3", 400, 65, 67, Remarks.Covered),
            OpenApiCoverageRow("POST", "/route3", 503, 0, 67, Remarks.NotCovered),
        )

        val resultStatistics = ResultStatistics(3, 0, 0, 0, 0)
        val report = utilReport.copy(coverageRows =  rows, statistics =  resultStatistics)

        assertThat(report.totalCoveragePercentage()).isEqualTo(81)
    }
}