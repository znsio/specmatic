package `in`.specmatic.test

import kotlin.math.roundToInt

class APICoverageReport(private val coveredAPIRows: List<APICoverageRow>, private val missedAPIRows: List<APICoverageRow>) {
    fun toLogString(): String {
        val maxPathSize: Int = coveredAPIRows.map { it.path.length }.max()

        val longestCoveragePercentageValue = "coverage"
        val statusFormat = "%${longestCoveragePercentageValue.length}s"
        val pathFormat = "%${maxPathSize}s"
        val methodFormat = "%${"method".length}s"
        val responseStatus = "%${"response".length}s"
        val countFormat = "%${"count".length}s"

        val tableHeader =
            "| ${statusFormat.format("coverage")} | ${pathFormat.format("path")} | ${methodFormat.format("method")} | ${responseStatus.format("response")} | ${
                countFormat.format("count")
            } |"
        val headerSeparator =
            "|-${"-".repeat(longestCoveragePercentageValue.length)}-|-${"-".repeat(maxPathSize)}-|-${methodFormat.format("------")}-|-${responseStatus.format("--------")}-|-${
                countFormat.format("-----")
            }-|"

        val headerTitleSize = tableHeader.length - 4
        val tableTitle = "| ${"%-${headerTitleSize}s".format("API COVERAGE SUMMARY")} |"
        val titleSeparator = "|-${"-".repeat(headerTitleSize)}-|"

        val nonZeroCoveragePercentageCounts = coveredAPIRows.count { it.coveragePercentage > 0 }
        val coveragePercentageSum = coveredAPIRows.sumOf { it.coveragePercentage }
        val totalCoveragePercentage = (coveragePercentageSum/nonZeroCoveragePercentageCounts).toDouble().roundToInt()

        val summary = "$totalCoveragePercentage% Coverage"
        val summaryRowFormatter = "%-${headerTitleSize}s"
        val summaryRow = "| ${summaryRowFormatter.format(summary)} |"

        val header: List<String> = listOf(titleSeparator, tableTitle, titleSeparator, tableHeader, headerSeparator)
        val body: List<String> = coveredAPIRows.map { it.toRowString(maxPathSize) }
        val footer: List<String> = listOf(titleSeparator, summaryRow, titleSeparator)

        val coveredAPIsTable =  (header + body + footer).joinToString(System.lineSeparator())

        // Missing APIs message

        val uncoveredCount = missedAPIRows.map { it.path }.distinct().filter { it.isNotEmpty() }.size
        val totalAPICount  = coveredAPIRows.map { it.path }.distinct().filter { it.isNotEmpty() }.size

        val missingAPIsMessageRows:MutableList<String> = mutableListOf()
        if(uncoveredCount > 0) {
            missingAPIsMessageRows.add("$uncoveredCount out of $totalAPICount APIs found missing in the specification")
        }

        return coveredAPIsTable + System.lineSeparator()  + missingAPIsMessageRows.joinToString(System.lineSeparator())
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