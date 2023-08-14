package `in`.specmatic.test

import `in`.specmatic.core.TestResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiCoverageReportGeneratorTest {
    @Test
    fun `test coverage report when all routes are covered`(){
        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200,  TestResult.Success),
            TestResultRecord("/route1", "POST", 401,  TestResult.Success),
            TestResultRecord("/route2", "GET", 200,  TestResult.Success),
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2"),
        )

        val apiCoverageReport = ApiCoverageReportGenerator(testReportRecords, applicationAPIs).generate()
        println(apiCoverageReport.toLogString())
        assertThat(apiCoverageReport).isEqualTo(APICoverageReport(
            listOf(
                APICoverageRow("GET", "/route1", 200, 1, CoverageStatus.Covered),
                APICoverageRow("POST", "", 200, 1, CoverageStatus.Covered),
                APICoverageRow("", "", 401, 1, CoverageStatus.Covered),
                APICoverageRow("GET", "/route2", 200, 1, CoverageStatus.Covered)
            ),
            listOf()
        ))
    }

    @Test
    fun `test coverage report when all some routes are missed`(){
        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200,  TestResult.Success),
            TestResultRecord("/route1", "POST", 401,  TestResult.Success),
            TestResultRecord("/route2", "GET", 200,  TestResult.Success),
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2"),
            API("GET", "/route3"),
        )

        val apiCoverageReport = ApiCoverageReportGenerator(testReportRecords, applicationAPIs).generate()
        println(apiCoverageReport.toLogString())
        assertThat(apiCoverageReport).isEqualTo(APICoverageReport(
            listOf(
                APICoverageRow("GET", "/route1", 200, 1, CoverageStatus.Covered),
                APICoverageRow("POST", "", 200, 1, CoverageStatus.Covered),
                APICoverageRow("", "", 401, 1, CoverageStatus.Covered),
                APICoverageRow("GET", "/route2", 200, 1, CoverageStatus.Covered)
            ),
            listOf(
                APICoverageRow("GET", "/route3", "", "", CoverageStatus.Missed)
            )
        ))
    }
}