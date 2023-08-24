package `in`.specmatic.test

import `in`.specmatic.core.TestResult
import `in`.specmatic.test.reports.coverage.OpenAPICoverageReport
import `in`.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import `in`.specmatic.test.reports.coverage.OpenApiCoverageRow
import `in`.specmatic.test.reports.coverage.Remarks
import `in`.specmatic.test.reports.renderers.CoverageReportTextRenderer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiCoverageReportInputTest {
    @Test
    fun `test coverage report when all endpoints are covered`() {
        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 401, TestResult.Success),
            TestResultRecord("/route2", "GET", 200, TestResult.Success),
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2")
        )

        val apiCoverageReport = OpenApiCoverageReportInput(testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageReport(
                listOf(
                    OpenApiCoverageRow("GET", "/route1", 200, 1, 100, Remarks.Covered),
                    OpenApiCoverageRow("POST", "", "200", "1", 0, Remarks.Covered),
                    OpenApiCoverageRow("", "", "401", "1", 0, Remarks.Covered),
                    OpenApiCoverageRow("GET", "/route2", 200, 1, 100, Remarks.Covered)
                ),
                2, 0, 0
            )
        )
    }

    @Test
    fun `test coverage report when some endpoints are partially covered`() {
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2"),
            API("POST", "/route2"),
            API("GET", "/route3"),
            API("POST", "/route3")
        )

        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 401, TestResult.Success),
            TestResultRecord("/route2", "GET", 200, TestResult.Success)
        )

        val apiCoverageReport = OpenApiCoverageReportInput(testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageReport(
                listOf(
                    OpenApiCoverageRow("GET", "/route1", 200, 1, 100, Remarks.Covered),
                    OpenApiCoverageRow("POST", "", 200, 1, 0, Remarks.Covered),
                    OpenApiCoverageRow("", "", 401, 1, 0, Remarks.Covered),
                    OpenApiCoverageRow("GET", "/route2", 200, 1, 50, Remarks.Covered),
                    OpenApiCoverageRow("POST", "", 0, 0, 0, Remarks.Missed),
                    OpenApiCoverageRow("GET", "/route3", 0, 0, 0, Remarks.Missed),
                    OpenApiCoverageRow("POST", "", 0, 0, 0, Remarks.Missed)
                ),
                3, 2, 0
            )
        )
    }

    @Test
    fun `test coverage report when some endpoints are marked as excluded`() {
        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 401, TestResult.Success),
            TestResultRecord("/route2", "GET", 200, TestResult.Success),
            TestResultRecord("/route2", "POST", 200, TestResult.Success)
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2"),
            API("POST", "/route2"),
            API("GET", "/healthCheck"),
            API("GET", "/heartbeat")
        )

        val excludedAPIs = mutableListOf(
            "/healthCheck",
            "/heartbeat"
        )


        val apiCoverageReport = OpenApiCoverageReportInput(testReportRecords, applicationAPIs, excludedAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageReport(
                listOf(
                    OpenApiCoverageRow("GET", "/route1", 200, 1, 100, Remarks.Covered),
                    OpenApiCoverageRow("POST", "", 200, 1, 0,  Remarks.Covered),
                    OpenApiCoverageRow("", "", 401, 1, 0,  Remarks.Covered),
                    OpenApiCoverageRow("GET", "/route2", 200, 1, 100,  Remarks.Covered),
                    OpenApiCoverageRow("POST", "", 200, 1, 0,  Remarks.Covered)
                ),
                2, 0, 0
            )
        )
    }

    @Test
    fun `test coverage report when some endpoints or operations are present in spec, but not implemented`() {
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2")
        )

        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200, TestResult.Success),
            TestResultRecord("/route2", "GET", 200, TestResult.Success),
            TestResultRecord("/route2", "POST", 200, TestResult.Failed)
        )

        val apiCoverageReport = OpenApiCoverageReportInput(testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageReport(
                listOf(
                    OpenApiCoverageRow("GET", "/route1", 200, 1, 100,  Remarks.Covered),
                    OpenApiCoverageRow("POST", "", 200, 1, 0,  Remarks.Covered),
                    OpenApiCoverageRow("GET", "/route2", 200, 1, 50,  Remarks.Covered),
                    OpenApiCoverageRow("POST", "", 200, 0, 0,  Remarks.NotImplemented)
                ),
                2, 0, 1
            )
        )
    }

    @Test
    fun `test coverage report when partially covered and partially implemented endpoints`() {
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2"),
            API("GET", "/route3"),
            API("POST", "/route3")
        )

        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200, TestResult.Success),
            TestResultRecord("/route2", "GET", 200, TestResult.Success),
            TestResultRecord("/route2", "POST", 200, TestResult.Failed)
        )

        val apiCoverageReport = OpenApiCoverageReportInput(testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageReport(
                listOf(
                    OpenApiCoverageRow("GET", "/route1", 200, 1, 100,  Remarks.Covered),
                    OpenApiCoverageRow("POST", "", 200, 1, 0,  Remarks.Covered),
                    OpenApiCoverageRow("GET", "/route2", 200, 1, 50,  Remarks.Covered),
                    OpenApiCoverageRow("POST", "", 200, 0, 0,  Remarks.NotImplemented),
                    OpenApiCoverageRow("GET", "/route3", 0, 0, 0,  Remarks.Missed),
                    OpenApiCoverageRow("POST", "", 0, 0, 0,  Remarks.Missed)
                ),
                3, 1, 1
            )
        )
    }
}