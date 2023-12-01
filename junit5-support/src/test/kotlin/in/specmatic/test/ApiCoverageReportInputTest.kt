package `in`.specmatic.test

import `in`.specmatic.core.TestResult
import `in`.specmatic.test.reports.coverage.*
import `in`.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import `in`.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import `in`.specmatic.test.reports.coverage.console.Remarks
import `in`.specmatic.test.reports.coverage.json.OpenApiCoverageJsonReport
import `in`.specmatic.test.reports.coverage.json.OpenApiCoverageJsonRow
import `in`.specmatic.test.reports.coverage.json.OpenApiCoverageOperation
import `in`.specmatic.test.reports.renderers.CoverageReportTextRenderer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiCoverageReportInputTest {
    companion object {
        const val CONFIG_FILE_PATH = "./specmatic.json"
    }
    @Test
    fun `test generates api coverage report when all endpoints are covered`() {
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

        val apiCoverageReport = OpenApiCoverageReportInput(CONFIG_FILE_PATH, testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100, Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", "200", "1", 0, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", "401", "1", 0, Remarks.Covered),
                    OpenApiCoverageConsoleRow("GET", "/route2", 200, 1, 100, Remarks.Covered)
                ),
                2,  0, 0, 0, 0
            )
        )
    }

    @Test
    fun `test generates api coverage report when some endpoints are partially covered`() {
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

        val apiCoverageReport = OpenApiCoverageReportInput(CONFIG_FILE_PATH, testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100, Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", 200, 1, 0, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 401, 1, 0, Remarks.Covered),
                    OpenApiCoverageConsoleRow("GET", "/route2", 200, 1, 50, Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", 0, 0, 0, Remarks.Missed),
                    OpenApiCoverageConsoleRow("GET", "/route3", 0, 0, 0, Remarks.Missed),
                    OpenApiCoverageConsoleRow("POST", "", 0, 0, 0, Remarks.Missed)
                ),
                3, 1, 0, 1, 0
            )
        )
    }

    @Test
    fun `test generates api coverage report when some endpoints are marked as excluded`() {
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


        val apiCoverageReport = OpenApiCoverageReportInput(CONFIG_FILE_PATH, testReportRecords, applicationAPIs, excludedAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100, Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", 200, 1, 0,  Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 401, 1, 0,  Remarks.Covered),
                    OpenApiCoverageConsoleRow("GET", "/route2", 200, 1, 100,  Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", 200, 1, 0,  Remarks.Covered)
                ),
                2,  0, 0, 0, 0
            )
        )
    }

    @Test
    fun `test generates empty api coverage report with all endpoints marked as excluded`() {
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
            "/route1",
            "/route2",
            "/healthCheck",
            "/heartbeat"
        )

        val apiCoverageReport = OpenApiCoverageReportInput(CONFIG_FILE_PATH, testReportRecords, applicationAPIs, excludedAPIs).generate()
        assertThat(apiCoverageReport.rows).isEmpty()
        assertThat(apiCoverageReport.totalCoveragePercentage).isEqualTo(0)
    }

    @Test
    fun `test generates empty api coverage report with all no paths are documented in the open api spec and endpoints api is not defined`() {
        val testReportRecords = mutableListOf<TestResultRecord>()
        val applicationAPIs = mutableListOf<API>()
        val excludedAPIs = mutableListOf<String>()

        val apiCoverageReport = OpenApiCoverageReportInput(CONFIG_FILE_PATH, testReportRecords, applicationAPIs, excludedAPIs).generate()
        assertThat(apiCoverageReport.rows).isEmpty()
    }

    @Test
    fun `test generates coverage report when some endpoints or operations are present in spec, but not implemented`() {
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1")
        )

        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200, TestResult.Success),
            TestResultRecord("/route2", "GET", 200, TestResult.Failed),
            TestResultRecord("/route2", "POST", 200, TestResult.Failed)
        )

        val apiCoverageReport = OpenApiCoverageReportInput(CONFIG_FILE_PATH, testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100,  Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", 200, 1, 0,  Remarks.Covered),
                    OpenApiCoverageConsoleRow("GET", "/route2", 200, 1, 0,  Remarks.NotImplemented),
                    OpenApiCoverageConsoleRow("POST", "", 200, 1, 0,  Remarks.NotImplemented)
                ),
                2, 0, 1, 0, 0
            )
        )
    }

    @Test
    fun `test generates api coverage report when partially covered and partially implemented endpoints`() {
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2"),
            API("GET", "/route3/{route_id}"),
            API("POST", "/route3/{route_id}")
        )

        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200, TestResult.Success),
            TestResultRecord("/route2", "GET", 200, TestResult.Success),
            TestResultRecord("/route2", "POST", 200, TestResult.Failed)
        )

        val apiCoverageReport = OpenApiCoverageReportInput(CONFIG_FILE_PATH, testReportRecords, applicationAPIs).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100,  Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", 200, 1, 0,  Remarks.Covered),
                    OpenApiCoverageConsoleRow("GET", "/route2", 200, 1, 50,  Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", 200, 1, 0,  Remarks.NotImplemented),
                    OpenApiCoverageConsoleRow("GET", "/route3/{route_id}", 0, 0, 0,  Remarks.Missed),
                    OpenApiCoverageConsoleRow("POST", "", 0, 0, 0,  Remarks.Missed)
                ),
                3, 1, 0, 0, 1
            )
        )
    }

    @Test
    fun `test generates api coverage json report with partially covered and partially implemented endpoints`() {
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2"),
            API("GET", "/route3/{route_id}"),
            API("POST", "/route3/{route_id}")
        )

        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route1.yaml", "HTTP"),
            TestResultRecord("/route1", "POST", 200, TestResult.Success, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route1.yaml", "HTTP"),
            TestResultRecord("/route2", "GET", 200, TestResult.Success, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route2.yaml", "HTTP"),
            TestResultRecord("/route2", "POST", 200, TestResult.Failed, "git", "https://github.com/znsio/specmatic-order-contracts.git", "main", "in/specmatic/examples/store/route2.yaml", "HTTP")
        )

        val openApiCoverageJsonReport = OpenApiCoverageReportInput(CONFIG_FILE_PATH, testReportRecords, applicationAPIs).generateJsonReport()
        assertThat(openApiCoverageJsonReport).isEqualTo(
            OpenApiCoverageJsonReport(
                CONFIG_FILE_PATH, listOf(
                    OpenApiCoverageJsonRow(
                        "git",
                        "https://github.com/znsio/specmatic-order-contracts.git",
                        "main",
                        "in/specmatic/examples/store/route1.yaml",
                        "HTTP",
                        listOf(
                            OpenApiCoverageOperation("/route1", "GET",200, 1, Remarks.Covered.toString()),
                            OpenApiCoverageOperation( "/route1", "POST",200, 1, Remarks.Covered.toString())
                        )
                    ),
                    OpenApiCoverageJsonRow(
                        "git",
                        "https://github.com/znsio/specmatic-order-contracts.git",
                        "main",
                        "in/specmatic/examples/store/route2.yaml",
                        "HTTP",
                        listOf(
                            OpenApiCoverageOperation( "/route2", "GET",200, 1, Remarks.Covered.toString()),
                            OpenApiCoverageOperation( "/route2", "POST",200, 1, Remarks.NotImplemented.toString())
                        )
                    ),
                    OpenApiCoverageJsonRow(
                        serviceType = "HTTP",
                        operations = listOf(
                            OpenApiCoverageOperation( "/route3/{route_id}", "GET",0, 0, Remarks.Missed.toString()),
                            OpenApiCoverageOperation( "/route3/{route_id}", "POST",0, 0, Remarks.Missed.toString()      )
                        )
                    )
                )
            )
        )

    }

    @Test
    fun `test generates api coverage report with endpoints present in spec but not tested`() {
        val testReportRecords = mutableListOf(
            TestResultRecord("/route1", "GET", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 200, TestResult.Success),
            TestResultRecord("/route1", "POST", 401, TestResult.Success),
            TestResultRecord("/route2", "GET", 200, TestResult.Success),
            TestResultRecord("/route2", "GET", 404, TestResult.Success),
            TestResultRecord("/route2", "POST", 500, TestResult.Success),
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/route1"),
            API("POST", "/route1"),
            API("GET", "/route2")
        )

        val allEndpoints = mutableListOf(
            Endpoint("/route1", "GET", 200),
            Endpoint("/route1", "POST", 200),
            Endpoint("/route1", "POST", 401),
            Endpoint("/route2", "GET", 200),
            Endpoint("/route2", "GET", 400)
        )

        val apiCoverageReport = OpenApiCoverageReportInput(CONFIG_FILE_PATH, testReportRecords, applicationAPIs, allEndpoints = allEndpoints).generate()
        println(CoverageReportTextRenderer().render(apiCoverageReport))
        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/route1", 200, 1, 100, Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", "200", "1", 0, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", "401", "1", 0, Remarks.Covered),
                    OpenApiCoverageConsoleRow("GET", "/route2", 200, 1, 75, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 400, 0, 0, Remarks.DidNotRun),
                    OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Covered),
                    OpenApiCoverageConsoleRow("POST", "", 500, 1, 0, Remarks.Covered)
                ),
                2, 0, 0, 0, 0
            )
        )
    }
}