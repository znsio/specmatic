package run.qontract.core.pattern

import io.cucumber.messages.Messages
import io.cucumber.messages.Messages.GherkinDocument.Feature.Scenario.Examples
import java.util.*

data class Examples(val columnNames: List<String> = mutableListOf(), val rows: MutableList<Row> = mutableListOf()) {
    val isEmpty: Boolean
        get() = rows.isEmpty()

    fun addRow(values: List<String>) {
        rows.add(Row(columnNames, values))
    }

    fun addRows(rows: List<Row>) {
        this.rows.addAll(rows)
    }

    fun getRow(index: Int): Row {
        return rows[index]
    }

    companion object {
        fun fromPSV(background: String): run.qontract.core.pattern.Examples {
            val rawLines = background.trim().split("\n".toRegex()).toMutableList()
            val columnNames = getValues(rawLines.first())
            val table = Examples(columnNames)

            for (line in rawLines.drop(1).map { it.trim() }) {
                if (line.isNotEmpty()) table.addRow(getValues(line))
            }

            return table
        }

        fun examplesFrom(examplesList: List<Examples>): List<run.qontract.core.pattern.Examples> = examplesList.map { examplesFrom(it) }

        fun examplesFrom(examples: Examples) =
            Examples(getColumnNames(examples)).apply {
                addRows(examples.tableBodyList.map { Row(this.columnNames, getValues(it))})
            }

        private fun getColumnNames(examples: Examples) = getValues(examples.tableHeader)

        private fun getValues(row: Messages.GherkinDocument.Feature.TableRow): ArrayList<String> = ArrayList(row.cellsList.map { it.value })

        private fun getValues(line: String): List<String> {
            val values = " $line ".split("\\|".toRegex()).map { value -> value.trim()}
            return values.drop(1).dropLast(1)
        }
    }

}