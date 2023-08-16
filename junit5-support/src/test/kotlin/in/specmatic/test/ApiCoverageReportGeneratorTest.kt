package `in`.specmatic.test

import `in`.specmatic.core.TestResult
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
        println(apiCoverageReport.toLogString())
        assertThat(apiCoverageReport).isEqualTo(
            APICoverageReport(
                listOf(
                    APICoverageRow("GET", "/route1", 200, 1, 100),
                    APICoverageRow("POST", "", "200", "1"),
                    APICoverageRow("", "", "401", "1"),
                    APICoverageRow("GET", "/route2", 200, 1, 100)
                ),
                listOf()
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
        println(apiCoverageReport.toLogString())
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
                listOf(
                    APICoverageRow("POST", "/route2", "", "", 0),
                    APICoverageRow("GET", "/route3", "", "", 0),
                    APICoverageRow("POST", "/route3", "", "", 0)
                )
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
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2"),
            API("POST", "/route2")
        )

        val excludedAPIs = mutableListOf(
            API("POST", "/route2")
        )

        val apiCoverageReport = ApiCoverageReportGenerator(testReportRecords, applicationAPIs, excludedAPIs).generate()
        println(apiCoverageReport.toLogString())
        assertThat(apiCoverageReport).isEqualTo(
            APICoverageReport(
                listOf(
                    APICoverageRow("GET", "/route1", 200, 1, 100),
                    APICoverageRow("POST", "", 200, 1, 0),
                    APICoverageRow("", "", 401, 1, 0),
                    APICoverageRow("GET", "/route2", 200, 1, 100)
                ),
                listOf()
            )
        )
    }
}