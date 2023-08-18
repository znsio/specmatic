package `in`.specmatic.test

import `in`.specmatic.core.TestResult
import `in`.specmatic.test.formatters.CoverageReportTextFormatter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiCoverageReportGeneratorTest {
    @Test
    fun `test coverage report when all routes are covered`() {
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

        val apiCoverageReport = ApiCoverageReportGenerator(testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextFormatter().format(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            APICoverageReport(
                listOf(
                    APICoverageRow("GET", "/route1", 200, 1, 100),
                    APICoverageRow("POST", "", "200", "1"),
                    APICoverageRow("", "", "401", "1"),
                    APICoverageRow("GET", "/route2", 200, 1, 100)
                ),
                2, 0
            )
        )
    }

    @Test
    fun `test coverage report when some routes are partially covered`() {
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
            TestResultRecord("/route2", "GET", 200, TestResult.Success),
        )

        val apiCoverageReport = ApiCoverageReportGenerator(testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextFormatter().format(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            APICoverageReport(
                listOf(
                    APICoverageRow("GET", "/route1", 200, 1, 100),
                    APICoverageRow("POST", "", 200, 1, 0),
                    APICoverageRow("", "", 401, 1, 0),
                    APICoverageRow("GET", "/route2", 200, 1, 50),
                    APICoverageRow("POST", "", 0, 0, 0),
                    APICoverageRow("GET", "/route3", 0, 0, 0),
                    APICoverageRow("POST", "", 0, 0, 0)
                ),
                3, 2
            )
        )
    }

    @Test
    fun `test coverage report when some routes are marked as excluded`() {
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


        val apiCoverageReport = ApiCoverageReportGenerator(testReportRecords, applicationAPIs, excludedAPIs).generate()
        println(CoverageReportTextFormatter().format(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            APICoverageReport(
                listOf(
                    APICoverageRow("GET", "/route1", 200, 1, 100),
                    APICoverageRow("POST", "", 200, 1, 0),
                    APICoverageRow("", "", 401, 1, 0),
                    APICoverageRow("GET", "/route2", 200, 1, 100),
                    APICoverageRow("POST", "", 200, 1, 0)
                ),
                2, 0
            )
        )
    }
}