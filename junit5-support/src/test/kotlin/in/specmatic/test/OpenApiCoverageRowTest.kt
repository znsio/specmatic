package `in`.specmatic.test

import `in`.specmatic.test.reports.coverage.OpenApiCoverageRow
import `in`.specmatic.test.reports.coverage.Remarks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiCoverageRowTest {

    @Test
    fun `test renders coverage percentage, path, method, response, count for top level row of covered endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("GET", path, 200, 1, 100, Remarks.Covered).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|     100% | /route1 |    GET |      200 |           1 |        Covered |")
    }

    @Test
    fun `test renders path, method, response, count with coverage percentage blank for sub level row of covered endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("POST", "", 200, 1, 0, Remarks.Missed).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|          |         |   POST |      200 |           1 |         Missed |")
    }

    @Test
    fun `test renders coverage percentage, path, method, with response, count for top level row of missed endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("GET", path, 0, 0, 0, Remarks.Missed).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|       0% | /route1 |    GET |          |           0 |         Missed |")
    }


    @Test
    fun `test renders coverage percentage, path, method, with response, count for sub level row of missed endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("POST", "", 0, 1, 0, Remarks.Missed).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|          |         |   POST |          |           1 |         Missed |")
    }

    @Test
    fun `test renders coverage percentage, path, method, with response, count for top level row of not implemented endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("GET", path, 0, 0, 0, Remarks.NotImplemented).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|       0% | /route1 |    GET |          |           0 | NotImplemented |")
    }

    @Test
    fun `test renders coverage percentage, path, method, with response, count for sub level row of not implemented endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("GET", "", 200, 0, 0, Remarks.NotImplemented).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|          |         |    GET |      200 |           0 | NotImplemented |")
    }
}