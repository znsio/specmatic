package io.specmatic.test

import io.specmatic.core.TestResult
import io.specmatic.test.reports.coverage.Endpoint
import io.specmatic.test.reports.coverage.ResultStatistics
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.reports.coverage.console.Remarks
import org.junit.jupiter.api.Test

class ApiCoverageReportTest {

    @Test
    fun `GET 200 in spec not implemented with actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200),
        )
        val applicationAPIs = mutableListOf<API>()
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.NotImplemented),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 404, 0, 50, Remarks.Missed, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 1, partiallyNotImplementedAPICount = 1
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 1, partiallyNotImplementedAPICount = 1
        )
        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 67, Remarks.NotImplemented),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 400, 1, 67, Remarks.NotImplemented, showPath = false, showMethod = false),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 404, 0, 67, Remarks.Missed, showPath = false, showMethod = false)
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
    }

    @Test
    fun `GET 200 in spec not implemented without actuator`() {
        val endpointsInSpec = mutableListOf(Endpoint("/order/{id}", "GET", 200))
        val testResultRecords =
            mutableListOf(TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404))
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 404, 0, 50, Remarks.Missed, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 1, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 67, Remarks.Covered),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 400, 1, 67, Remarks.Covered, showPath = false, showMethod = false),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 404, 0, 67, Remarks.Missed, showPath = false, showMethod = false)
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 1, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.NotImplemented),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 404, 0, 50, Remarks.NotCovered, showPath = false, showMethod = false)
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 1
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIS)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 67, Remarks.NotImplemented),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 400, 1, 67, Remarks.NotImplemented, showPath = false, showMethod = false),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 404, 0, 67, Remarks.NotCovered, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 1
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
    }

    @Test
    fun `GET 200 and 404 in spec not implemented without actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200), Endpoint("/order/{id}", "GET", 404)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 404, 0, 50, Remarks.NotCovered, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 67, Remarks.Covered),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 400, 1, 67, Remarks.Covered, showPath = false, showMethod = false),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 404, 0, 67, Remarks.NotCovered, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 404, 0, 50, Remarks.NotCovered, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 67, Remarks.Covered),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 400, 1, 67, Remarks.Covered, showPath = false, showMethod = false),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 404, 0, 67, Remarks.NotCovered, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
    }

    @Test
    fun `GET 200 and 400 in spec implemented without actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200), Endpoint("/order/{id}", "GET", 400)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Success, actualResponseStatus = 200)
        )
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 400, 0, 50, Remarks.NotCovered, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 201, 1, 67, Remarks.Covered),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 400, 1, 67, Remarks.Covered, showPath = false, showMethod = false),
            OpenApiCoverageConsoleRow("POST", "/order/{id}", 404, 0, 67, Remarks.NotCovered, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 404, 0, 50, Remarks.Missed, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 1, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
    }

    @Test
    fun `GET 200 in spec implemented without actuator bad request`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 404, 0, 50, Remarks.Missed, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 1, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
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
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 404, 0, 50, Remarks.NotCovered, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
    }

    @Test
    fun `GET 200 and 404 in spec implemented without actuator bad request`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/order/{id}", "GET", 200), Endpoint("/order/{id}", "GET", 404)
        )
        val testResultRecords = mutableListOf(
            TestResultRecord("/order/{id}", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/order/{id}", 404, 0, 50, Remarks.NotCovered, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
    }

    // FOLLOWING TESTS ARE FOR INVALID REMARK (Actuator Doesn't Matter)

    @Test
    fun `No Param GET 200 in spec not implemented with actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/orders", "GET", 200)
        )
        val applicationAPIs = mutableListOf<API>()

        val testResultRecords = mutableListOf(
            TestResultRecord("/orders", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/orders", 200, 1, 50, Remarks.NotImplemented),
            OpenApiCoverageConsoleRow("GET", "/orders", 404, 0, 50, Remarks.Missed, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 1, partiallyNotImplementedAPICount = 1
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
    }

    @Test
    fun `No Param GET 200 and 404 in spec not implemented without actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/orders", "GET", 200), Endpoint("/orders", "GET", 404)
        )

        val testResultRecords = mutableListOf(
            TestResultRecord("/orders", "GET", 200, TestResult.Failed, actualResponseStatus = 404)
        )
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/orders", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/orders", 404, 0, 50, Remarks.Invalid, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
    }

    @Test
    fun `No Param GET 200 and 404 in spec implemented with actuator`() {
        val endpointsInSpec = mutableListOf(
            Endpoint("/orders", "GET", 200), Endpoint("/orders", "GET", 404)
        )
        val applicationAPIs = mutableListOf(
            API("GET", "/orders")
        )

        val testResultRecords = mutableListOf(
            TestResultRecord("/orders", "GET", 200, TestResult.Success, actualResponseStatus = 200)
        )
        val actualCoverageReport = generateCoverageReport(testResultRecords, endpointsInSpec, applicationAPIs)

        val expectedCoverageRows = listOf(
            OpenApiCoverageConsoleRow("GET", "/orders", 200, 1, 50, Remarks.Covered),
            OpenApiCoverageConsoleRow("GET", "/orders", 404, 0, 50, Remarks.Invalid, showPath = false, showMethod = false),
        )
        val expectedResultStatistics = ResultStatistics(
            totalEndpointsCount = 1, missedEndpointsCount = 0, notImplementedAPICount = 0,
            partiallyMissedEndpointsCount = 0, partiallyNotImplementedAPICount = 0
        )

        assertReportEquals(actualCoverageReport, expectedCoverageRows, expectedResultStatistics)
    }
}
