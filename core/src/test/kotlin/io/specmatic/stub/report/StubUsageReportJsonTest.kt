package io.specmatic.stub.report

import io.specmatic.stub
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class StubUsageReportJsonTest {

    @Test
    fun `append an empty existing report does not change the new report`() {
        val newReport = StubUsageReportJson(
            specmaticConfigPath = "./specmatic.yaml",
            stubUsage = listOf(
                stubUsageReportRow(1, "/path1", "GET", 200)
            )
        )

        val existingReport = StubUsageReportJson(
            specmaticConfigPath = "./specmatic.yaml",
            stubUsage = emptyList()
        )

        val expectedMergedReport = StubUsageReportJson(
            specmaticConfigPath = "./specmatic.yaml",
            stubUsage = listOf(
                stubUsageReportRow(1, "/path1", "GET", 200)
            )
        )

        val mergedReport = newReport.append(existingReport)

        assertThat(mergedReport).isEqualTo(expectedMergedReport)
    }

    @Test
    fun `append an new report with existing report with additional counts sums the count`() {
        val newReport = StubUsageReportJson(
            specmaticConfigPath = "./specmatic.yaml",
            stubUsage = listOf(
                stubUsageReportRow(1, "/path1", "GET", 200)
            )
        )

        val existingReport = StubUsageReportJson(
            specmaticConfigPath = "./specmatic.yaml",
            stubUsage = listOf(
                stubUsageReportRow(2, "/path1", "GET", 200)
            )
        )

        val expectedMergedReport = StubUsageReportJson(
            specmaticConfigPath = "./specmatic.yaml",
            stubUsage = listOf(
                stubUsageReportRow(3, "/path1", "GET", 200)
            )
        )

        val mergedReport = newReport.append(existingReport)

        assertThat(mergedReport).isEqualTo(expectedMergedReport)
    }

    private fun stubUsageReportRow(count: Int, path: String, httpMethod: String, responseCode: Int): StubUsageReportRow {
        return StubUsageReportRow(
            type = "git",
            repository = "https://github.com/znsio/specmatic-order-contracts.git",
            branch = "main",
            specification = "in/specmatic/examples/store/route1.yaml",
            serviceType = "HTTP",
            operations = listOf(
                StubUsageReportOperation(
                    path = path,
                    method = httpMethod,
                    responseCode = responseCode,
                    count = count
                )
            )
        )
    }
}