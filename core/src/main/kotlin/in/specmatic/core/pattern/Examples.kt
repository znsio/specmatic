package `in`.specmatic.core.pattern

import io.cucumber.messages.types.Examples
import io.cucumber.messages.types.TableRow
import java.util.*

data class Examples(val columnNames: List<String> = emptyList(), val rows: List<Row> = listOf()) {
    val isEmpty: Boolean
        get() = rows.isEmpty()

    companion object {
        fun examplesFrom(examplesList: List<Examples>): List<`in`.specmatic.core.pattern.Examples> = examplesList.map { examplesFrom(it) }

        fun examplesFrom(examples: Examples): `in`.specmatic.core.pattern.Examples {
            val columns: List<String> = getColumnNames(examples)
            val rows = examples.tableBody.map { Row(columns, getValues(it)) }

            return Examples(columns, rows)
        }

        private fun getColumnNames(examples: Examples) = getValues(examples.tableHeader)

        private fun getValues(row: TableRow): List<String> = row.cells.map { it.value }

    }

}