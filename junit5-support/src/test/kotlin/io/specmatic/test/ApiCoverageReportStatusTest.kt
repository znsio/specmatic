package io.specmatic.test

import io.specmatic.core.TestResult
import io.specmatic.test.reports.coverage.Endpoint
import io.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.reports.coverage.console.Remarks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiCoverageReportStatusTest {

    companion object {
        const val CONFIG_FILE_PATH = "./specmatic.json"
    }

    @Test
    fun `identifies endpoint as 'covered' when contract test passes and route+method is present in actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/route1", "GET", 200)
        )

        val applicationAPIs = mutableListOf(
            API("GET", "/route1")
        )

        val contractTestResults = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
        )

        val apiCoverageReport = OpenApiCoverageReportInput(
            CONFIG_FILE_PATH,
            contractTestResults,
            applicationAPIs,
            allEndpoints = endpointsInSpec,
            endpointsAPISet = true
        ).generate()
        assertThat(apiCoverageReport.rows).isEqualTo(
            listOf(
                OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100, Remarks.Covered)
            )
        )
    }

    @Test
    fun `identifies endpoint as 'covered' when contract test passes and route+method is not present in actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/route1", "GET", 200),
        )

        val applicationAPIs = mutableListOf<API>()

        val contractTestResults = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
        )

        val apiCoverageReport = OpenApiCoverageReportInput(
            CONFIG_FILE_PATH,
            contractTestResults,
            applicationAPIs,
            allEndpoints = endpointsInSpec,
            endpointsAPISet = true
        ).generate()
        assertThat(apiCoverageReport.rows).isEqualTo(
            listOf(
                OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100, Remarks.Covered)
            )
        )
    }

    @Test
    fun `identifies endpoint as 'covered' when contract test fails and route+method is present in actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/route1", "GET", 200),
        )

        val applicationAPIs = mutableListOf(
            API("GET", "/route1")
        )

        val contractTestResults = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Failed, actualResponseStatus = 400)
        )

        val apiCoverageReport = OpenApiCoverageReportInput(
            CONFIG_FILE_PATH,
            contractTestResults,
            applicationAPIs,
            allEndpoints = endpointsInSpec,
            endpointsAPISet = true
        ).generate()
        assertThat(apiCoverageReport.rows).isEqualTo(
            listOf(
                OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 50, Remarks.Covered),
                OpenApiCoverageConsoleRow("", "", 400, 1, 0, Remarks.Missed)
            )
        )
    }

    @Test
    fun `identifies endpoint as 'not covered' when contract test fails and actuator is not available`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/route1", "GET", 200)
        )

        val applicationAPIs = mutableListOf<API>()

        val contractTestResults = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Failed, actualResponseStatus = 400),
        )

        val apiCoverageReport = OpenApiCoverageReportInput(
            CONFIG_FILE_PATH,
            contractTestResults,
            applicationAPIs,
            allEndpoints = endpointsInSpec,
            endpointsAPISet = false
        ).generate()
        assertThat(apiCoverageReport.rows).isEqualTo(
            listOf(
                OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 0, Remarks.NotCovered),
                OpenApiCoverageConsoleRow("", "", 400, 1, 0, Remarks.Missed)
            )
        )
    }

    @Test
    fun `identifies endpoint as 'not implemented' when contract test fails, and route+method is not present in actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/route1", "GET", 200),
            Endpoint("/route2", "GET", 200)
        )

        val applicationAPIs = mutableListOf(
            API("GET", "/route1")
        )

        val contractTestResults = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success, actualResponseStatus = 200),
            TestResultRecord("/route2", "GET", 200, TestResult.Failed, actualResponseStatus = 404),
        )

        val apiCoverageReport = OpenApiCoverageReportInput(
            CONFIG_FILE_PATH,
            contractTestResults,
            applicationAPIs,
            allEndpoints = endpointsInSpec,
            endpointsAPISet = true
        ).generate()
        assertThat(apiCoverageReport.rows).isEqualTo(
            listOf(
                OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100, Remarks.Covered),
                OpenApiCoverageConsoleRow("GET", "/route2", 200, 1, 0, Remarks.NotImplemented),
                OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Missed)
            )
        )
    }

    @Test
    fun `identifies endpoint as 'missing in spec' when route+method is present in actuator but not present in the spec`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/route1", "GET", 200)
        )

        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("GET", "/route2")
        )

        val contractTestResults = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success)
        )

        val apiCoverageReport = OpenApiCoverageReportInput(
            CONFIG_FILE_PATH,
            contractTestResults,
            applicationAPIs,
            allEndpoints = endpointsInSpec,
            endpointsAPISet = true
        ).generate()
        assertThat(apiCoverageReport.rows).isEqualTo(
            listOf(
                OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100, Remarks.Covered),
                OpenApiCoverageConsoleRow("GET", "/route2", 0, 0, 0, Remarks.Missed)
            )
        )
    }

    @Test
    fun `identifies endpoint as 'did not run' when contract test is not generated for an endpoint present in the spec`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/route1", "GET", 200),
            Endpoint("/route1", "GET", 400),
        )

        val applicationAPIs = mutableListOf(
            API("GET", "/route1")
        )

        val contractTestResults = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success)
        )

        val apiCoverageReport = OpenApiCoverageReportInput(
            CONFIG_FILE_PATH,
            contractTestResults,
            applicationAPIs,
            allEndpoints = endpointsInSpec,
            endpointsAPISet = true
        ).generate()
        assertThat(apiCoverageReport.rows).isEqualTo(
            listOf(
                OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 50, Remarks.Covered),
                OpenApiCoverageConsoleRow("", "", 400, 0, 0, Remarks.DidNotRun)
            )
        )
    }
}