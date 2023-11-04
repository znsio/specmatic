package `in`.specmatic.test.reports.renderers

class Table(private val title: String, private val header: List<String>, private val rows: List<List<String>>, private val summary: String?) {
    fun render(): String {
        val columnCount = (listOf(header.size) + rows.map { it.size }).max()
        val columnWidths = (0 until columnCount).map { columnIndex ->
            (listOf(header.getOrNull(columnIndex)?.length ?: 0) + rows.map { it.getOrNull(columnIndex)?.length ?: 0 }).max()
        }

        val headersWithEqualizedLengths = header + (0 until (columnCount - header.size)).map { "" }
        val tableHeader = headersWithEqualizedLengths.mapIndexed { index, column ->
            "%-${columnWidths[index]}s".format(column)
        }.joinToString(" | ", prefix = "| ", postfix = " |")

        val headerSeparator = columnWidths.joinToString("+", prefix = "|", postfix = "|") { "-".repeat(it + 2) }

        val headerTitleSize = tableHeader.length - 4
        val tableTitle = "| ${"%-${headerTitleSize}s".format(title)} |"
        val titleSeparator = "|-${"-".repeat(headerTitleSize)}-|"

        val header: List<String> = listOf(titleSeparator, tableTitle, titleSeparator, tableHeader, headerSeparator)
        val rowsWithEqualizedLength = rows.map { row ->
            row + (0 until (columnCount - row.size)).map { "" }
        }
        val tableBody: List<String> = rowsWithEqualizedLength.map { row ->
            row.mapIndexed { index, column ->
                "%-${columnWidths[index]}s".format(column)
            }.joinToString(" | ", prefix = "| ", postfix = " |")
        }

        val footer: List<String> = summary?.let {
            val summaryRowFormatter = "%-${headerTitleSize}s"
            val summaryRow = "| ${summaryRowFormatter.format(summary)} |"

            listOf(titleSeparator, summaryRow, titleSeparator)
        } ?: listOf(titleSeparator)

        val coveredAPIsTable =  (header + tableBody + footer).joinToString(System.lineSeparator())

        return coveredAPIsTable

    }
}
