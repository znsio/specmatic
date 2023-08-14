package `in`.specmatic.test

class APICoverageReport(private val coveredAPIRows: List<APICoverageRow>, private val missedAPIRows: List<APICoverageRow>) {
    fun toLogString(): String {
        val maxPathSize: Int = coveredAPIRows.map { it.path.length }.plus(missedAPIRows.map { it.path.length }).max()

        val longestStatus = longestStatus()
        val statusFormat = "%${longestStatus.length}s"
        val pathFormat = "%${maxPathSize}s"
        val methodFormat = "%${"method".length}s"
        val responseStatus = "%${"response".length}s"
        val countFormat = "%${"count".length}s"

        val tableHeader =
            "| ${statusFormat.format("status")} | ${pathFormat.format("path")} | ${methodFormat.format("method")} | ${responseStatus.format("response")} | ${
                countFormat.format("count")
            } |"
        val headerSeparator =
            "|-${"-".repeat(longestStatus.length)}-|-${"-".repeat(maxPathSize)}-|-${methodFormat.format("------")}-|-${responseStatus.format("--------")}-|-${
                countFormat.format("-----")
            }-|"

        val headerTitleSize = tableHeader.length - 4
        val tableTitle = "| ${"%-${headerTitleSize}s".format("API COVERAGE SUMMARY")} |"
        val titleSeparator = "|-${"-".repeat(headerTitleSize)}-|"

        val coveredCount = coveredAPIRows.map { it.path }.distinct().filter { it.isNotEmpty() }.size
        val uncoveredCount = missedAPIRows.map { it.path }.distinct().filter { it.isNotEmpty() }.size
        val total = coveredCount + uncoveredCount

        val summary = "$coveredCount / $total APIs covered"
        val summaryRowFormatter = "%-${headerTitleSize}s"
        val summaryRow = "| ${summaryRowFormatter.format(summary)} |"

        val header: List<String> = listOf(titleSeparator, tableTitle, titleSeparator, tableHeader, headerSeparator)
        val body: List<String> = (coveredAPIRows + missedAPIRows).map { it.toRowString(maxPathSize) }
        val footer: List<String> = listOf(titleSeparator, summaryRow, titleSeparator)

        return (header + body + footer).joinToString(System.lineSeparator())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is APICoverageReport) return false
        return coveredAPIRows == other.coveredAPIRows && missedAPIRows == other.missedAPIRows
    }

    override fun hashCode(): Int {
        var result = coveredAPIRows.hashCode()
        result = 31 * result + missedAPIRows.hashCode()
        return result
    }
}