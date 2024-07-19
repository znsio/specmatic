package io.specmatic.test

import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import io.specmatic.test.reports.coverage.console.Remarks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiCoverageConsoleRowTest {

    @Test
    fun `test renders coverage percentage, path, method, response, count for top level row of covered endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageConsoleRow("GET", path, 200, 1, 100, Remarks.Covered).toRowString(
            path.length,
            Remarks.Covered.toString().length,
        )
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|     100% | /route1 | GET    |      200 |           1 | covered |")
    }

    @Test
    fun `test renders path, method, response, count with coverage percentage blank for sub level row of covered endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageConsoleRow("POST", "", 200, 1, 0, Remarks.Missed).toRowString(
            path.length,
            Remarks.Missed.toString().length
        )
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|          |         | POST   |      200 |           1 | missing in spec |")
    }

    @Test
    fun `test renders coverage percentage, path, method, with response, count for top level row of missed endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageConsoleRow("GET", path, 0, 0, 0, Remarks.Missed).toRowString(
            path.length,
            Remarks.Missed.toString().length
        )
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|       0% | /route1 | GET    |          |           0 | missing in spec |")
    }


    @Test
    fun `test renders coverage percentage, path, method, with response, count for sub level row of missed endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageConsoleRow("POST", "", 0, 1, 0, Remarks.Missed).toRowString(
            path.length,
            Remarks.Missed.toString().length
        )
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|          |         | POST   |          |           1 | missing in spec |")
    }

    @Test
    fun `test renders coverage percentage, path, method, with response, count for top level row of not implemented endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageConsoleRow("GET", path, 0, 0, 0, Remarks.NotImplemented).toRowString(
            path.length,
            Remarks.NotImplemented.toString().length
        )
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|       0% | /route1 | GET    |          |           0 | not implemented |")
    }

    @Test
    fun `test renders coverage percentage, path, method, with response, count for sub level row of not implemented endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageConsoleRow("GET", "", 200, 0, 0, Remarks.NotImplemented).toRowString(
            path.length,
            Remarks.NotImplemented.toString().length
        )
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|          |         | GET    |      200 |           0 | not implemented |")
    }
}