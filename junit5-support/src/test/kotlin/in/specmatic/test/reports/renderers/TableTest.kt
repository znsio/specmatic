package `in`.specmatic.test.reports.renderers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TableTest {
    @Test
    fun `should generate a table given title summary and rows`() {
        val table = Table("Prices", listOf("Region", "Apples", "Oranges"), listOf(listOf("North", "10", "20"), listOf("South", "100", "120")), "Summary")
        assertThat(table.render()).isEqualTo(
            """
                |---------------------------|
                | Prices                    |
                |---------------------------|
                | Region | Apples | Oranges |
                |--------+--------+---------|
                | North  | 10     | 20      |
                | South  | 100    | 120     |
                |---------------------------|
                | Summary                   |
                |---------------------------|
            """.trimIndent()
        )
    }

    @Test
    fun `should generate a table with no last line if the summary is missing`() {
        val table = Table("Prices", listOf("Region", "Apples", "Oranges"), listOf(listOf("North", "10", "20"), listOf("South", "100", "120")), null)
        assertThat(table.render()).isEqualTo(
            """
                |---------------------------|
                | Prices                    |
                |---------------------------|
                | Region | Apples | Oranges |
                |--------+--------+---------|
                | North  | 10     | 20      |
                | South  | 100    | 120     |
                |---------------------------|
            """.trimIndent()
        )
    }

    @Test
    fun `should handle columns of differing lengths`() {
        val table = Table("Prices", listOf("Region", "Apples", "Oranges", "Pears"), listOf(listOf("North", "10", "20"), listOf("South", "100", "120"), listOf("East", "100", "50", "300")), "Summary")
        assertThat(table.render()).isEqualTo(
            """
                |-----------------------------------|
                | Prices                            |
                |-----------------------------------|
                | Region | Apples | Oranges | Pears |
                |--------+--------+---------+-------|
                | North  | 10     | 20      |       |
                | South  | 100    | 120     |       |
                | East   | 100    | 50      | 300   |
                |-----------------------------------|
                | Summary                           |
                |-----------------------------------|
            """.trimIndent()
        )
    }

    @Test
    fun `should handle more row columns than header columns`() {
        val table = Table("Prices", listOf("Region", "Apples", "Oranges"), listOf(listOf("North", "10", "20"), listOf("South", "100", "120"), listOf("East", "100", "50", "300")), "Summary")
        assertThat(table.render()).isEqualTo(
            """
                |---------------------------------|
                | Prices                          |
                |---------------------------------|
                | Region | Apples | Oranges |     |
                |--------+--------+---------+-----|
                | North  | 10     | 20      |     |
                | South  | 100    | 120     |     |
                | East   | 100    | 50      | 300 |
                |---------------------------------|
                | Summary                         |
                |---------------------------------|
            """.trimIndent()
        )
    }
}