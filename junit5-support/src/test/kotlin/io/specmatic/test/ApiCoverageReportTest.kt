package io.specmatic.test

import io.specmatic.core.TestResult
import io.specmatic.test.ApiCoverageReportInputTest.Companion.CONFIG_FILE_PATH
import io.specmatic.test.reports.coverage.Endpoint
import io.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.reports.coverage.console.Remarks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiCoverageReportTest {

    companion object {
        private fun generateCoverageReport(
            testResultRecords: MutableList<TestResultRecord>,
            allEndpoints: MutableList<Endpoint>,
            applicationAPIS: MutableList<API>? = null,
        ): OpenAPICoverageConsoleReport {
            if (applicationAPIS != null) {
                return OpenApiCoverageReportInput(
                    CONFIG_FILE_PATH,
                    testResultRecords,
                    applicationAPIS,
                    allEndpoints = allEndpoints,
                    endpointsAPISet = true
                ).generate()
            }
            return OpenApiCoverageReportInput(
                CONFIG_FILE_PATH, testResultRecords, allEndpoints = allEndpoints
            ).generate()
        }
    }

    @Test
    fun `GET 200 in spec not implemented with actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200),
        )
        val applicationAPIs = mutableListOf<API>()
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 0, Remarks.NotImplemented),
                    OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Missed),
                ), 1, 0, 0, 1, 1
            )
        )
    }

    @Test
    fun `POST 201 and 400 in spec not implemented with actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "POST", 201), Endpoint("/order/{id}", "POST", 400)
        )
        val applicationAPIs = mutableListOf<API>()
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "POST", 201, TestResult.Failed, actualResponseStatus = 404),
            TestResultRecord("/order/{id}", "POST", 400, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 0, Remarks.NotImplemented),
                    OpenApiCoverageConsoleRow("", "", 400, 1, 0, Remarks.NotImplemented),
                    OpenApiCoverageConsoleRow("", "", 404, 2, 0, Remarks.Missed)
                ), 1, 0, 0, 1, 1
            )
        )

    }

    @Test
    fun `GET 200 in spec not implemented without actuator`() {
        val endpointsInSpec = mutableListOf(Endpoint("/order/{id}", "GET", 200))
        val testResultRecords =
            mutableListOf(TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404))
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 0, Remarks.NotCovered),
                    OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Missed),
                ), 1, 0, 0, 1, 0
            )
        )
    }

    @Test
    fun `POST 201 and 400 in spec not implemented without actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "POST", 201), Endpoint("/order/{id}", "POST", 400)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "POST", 201, TestResult.Failed, actualResponseStatus = 404),
            TestResultRecord("/order/{id}", "POST", 400, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 0, Remarks.NotCovered),
                    OpenApiCoverageConsoleRow("", "", 400, 1, 0, Remarks.NotCovered),
                    OpenApiCoverageConsoleRow("", "", 404, 2, 0, Remarks.Missed)
                ), 1, 0, 0, 1, 0
            )
        )
    }

    @Test
    fun `GET 200 and 404 in spec not implemented with actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200), Endpoint("/order/{id}", "GET", 404)
        )
        val applicationAPIs = mutableListOf<API>()
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.NotImplemented),
                    OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Covered),
                ), 1, 0, 0, 0, 1
            )
        )
    }

    @Test
    fun `POST 201, 400 and 404 in spec not implemented with actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "POST", 201),
            Endpoint("/order/{id}", "POST", 400),
            Endpoint("/order/{id}", "POST", 404)
        )
        val applicationAPIS = mutableListOf<API>()
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "POST", 201, TestResult.Failed, actualResponseStatus = 404),
            TestResultRecord("/order/{id}", "POST", 400, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIS)


        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 33, Remarks.NotImplemented),
                    OpenApiCoverageConsoleRow("", "", 400, 1, 0, Remarks.NotImplemented),
                    OpenApiCoverageConsoleRow("", "", 404, 2, 0, Remarks.Covered),
                ), 1, 0, 0, 0, 1
            )
        )
    }

    @Test
    fun `GET 200 and 404 in spec not implemented without actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200), Endpoint("/order/{id}", "GET", 404)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.NotCovered),
                    OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Covered),
                ), 1, 0, 0, 0, 0
            )
        )
    }

    @Test
    fun `POST 201, 400 and 404 in spec not implemented without actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "POST", 201),
            Endpoint("/order/{id}", "POST", 400),
            Endpoint("/order/{id}", "POST", 404)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "POST", 201, TestResult.Failed, actualResponseStatus = 404),
            TestResultRecord("/order/{id}", "POST", 400, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 33, Remarks.NotCovered),
                    OpenApiCoverageConsoleRow("", "", 400, 1, 0, Remarks.NotCovered),
                    OpenApiCoverageConsoleRow("", "", 404, 2, 0, Remarks.Covered),
                ), 1, 0, 0, 0, 0
            )
        )
    }

    @Test
    fun `GET 200 and 404 in spec implemented with actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200), Endpoint("/order/{id}", "GET", 404)
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/order/{id}")
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Success, actualResponseStatus = 200)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)


        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 404, 0, 0, Remarks.DidNotRun),
                ), 1, 0, 0, 0, 0
            )
        )
    }

    @Test
    fun `POST 201, 400 and 404 in spec implemented with actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "POST", 201),
            Endpoint("/order/{id}", "POST", 400),
            Endpoint("/order/{id}", "POST", 404)
        )
        val applicationAPIs = mutableListOf(
            API("POST", "/order/{id}")
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "POST", 201, TestResult.Success, actualResponseStatus = 201),
            TestResultRecord("/order/{id}", "POST", 400, TestResult.Success, actualResponseStatus = 400)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 67, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 400, 1, 0, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 404, 0, 0, Remarks.DidNotRun),
                ), 1, 0, 0, 0, 0
            )
        )

    }

    @Test
    fun `GET 200 and 400 in spec implemented without actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200), Endpoint("/order/{id}", "GET", 400)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Success, actualResponseStatus = 200)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 400, 0, 0, Remarks.DidNotRun),
                ), 1, 0, 0, 0, 0
            )
        )
    }

    @Test
    fun `POST 201, 400 and 404 in spec implemented without actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "POST", 201),
            Endpoint("/order/{id}", "POST", 400),
            Endpoint("/order/{id}", "POST", 404)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "POST", 201, TestResult.Success, actualResponseStatus = 201),
            TestResultRecord("/order/{id}", "POST", 400, TestResult.Success, actualResponseStatus = 400)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 67, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 400, 1, 0, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 404, 0, 0, Remarks.DidNotRun),
                ), 1, 0, 0, 0, 0
            )
        )
    }

    // FOLLOWING TESTS ARE BAD REQUEST TESTS FOR GET ENDPOINT

    @Test
    fun `GET 200 in spec implemented with actuator bad request`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200)
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/order/{id}")
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Missed),
                ), 1, 0, 0, 1, 0
            )
        )
    }

    @Test
    fun `GET 200 in spec implemented without actuator bad request`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 0, Remarks.NotCovered),
                    OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Missed),
                ), 1, 0, 0, 1, 0
            )
        )
    }

    @Test
    fun `GET 200 and 404 in spec implemented with actuator bad request`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200), Endpoint("/order/{id}", "GET", 404)
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/order/{id}")
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 100, Remarks.Covered),
                    OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Covered),
                ), 1, 0, 0, 0, 0
            )
        )
    }

    @Test
    fun `GET 200 and 404 in spec implemented without actuator bad request`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200), Endpoint("/order/{id}", "GET", 404)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val apiCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        assertThat(apiCoverageReport).isEqualTo(
            OpenAPICoverageConsoleReport(
                listOf(
                    OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.NotCovered),
                    OpenApiCoverageConsoleRow("", "", 404, 1, 0, Remarks.Covered),
                ), 1, 0, 0, 0, 0
            )
        )
    }
}
