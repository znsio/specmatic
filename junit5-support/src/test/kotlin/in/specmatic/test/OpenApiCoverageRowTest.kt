package `in`.specmatic.test

import `in`.specmatic.test.reports.coverage.OpenApiCoverageRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiCoverageRowTest {

    @Test
    fun `test renders coverage percentage, path, method, response, count for top level row of covered endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("GET", path, 200, 1, 100).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|     100% | /route1 |    GET |      200 |           1 |")
    }

    @Test
    fun `test renders path, method, response, count with coverage percentage blank for sub level row of covered endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("POST", "", 200, 1, 0).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|          |         |   POST |      200 |           1 |")
    }

    @Test
    fun `test renders coverage percentage, path, method, with response, count for top level row of missed endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("GET", path, 0, 0, 0).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|       0% | /route1 |    GET |          |           0 |")
    }


    @Test
    fun `test renders coverage percentage, path, method, with response, count for sub level row of missed endpoint`() {
        val path  = "/route1"
        val coverageRowString = OpenApiCoverageRow("POST", "", 0, 1, 0).toRowString(path.length)
        println(coverageRowString)
        assertThat(coverageRowString).isEqualTo("|          |         |   POST |          |           1 |")
    }
}