package io.specmatic.test

import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.reports.coverage.console.Remarks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiCoverageConsoleReportTest {

    @Test
    fun `test calculates total percentage based on number of covered endpoints`() {
        val rows = listOf(
            OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("POST", "/route1", 200, 0, 0, Remarks.Missed),
            OpenApiCoverageConsoleRow("GET", "/route2", 200, 1, 25, Remarks.Covered),
            OpenApiCoverageConsoleRow("POST", "/route2", 200, 0, 0, Remarks.NotCovered),
            OpenApiCoverageConsoleRow("POST", "/route2", 400, 0, 0, Remarks.NotCovered),
            OpenApiCoverageConsoleRow("POST", "/route2", 500, 0, 0, Remarks.NotCovered),
            OpenApiCoverageConsoleRow("GET", "/route3", 200, 0, 0, Remarks.NotCovered),
        )

        val coverageReport = OpenAPICoverageConsoleReport(rows, totalEndpointsCount = 3, missedEndpointsCount = 0, notImplementedAPICount = 0, partiallyMissedEndpointsCount = 1, partiallyNotImplementedAPICount = 0)

        assertThat(coverageReport.totalCoveragePercentage).isEqualTo(28)
    }
}