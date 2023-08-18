package `in`.specmatic.test.formatters

import `in`.specmatic.test.APICoverageReport

class CoverageReportTextFormatter: ReportFormatter<APICoverageReport> {
    override fun format(report: APICoverageReport): String {
        val maxPathSize: Int = report.rows.map { it.path.length }.max()

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

        val totalCoveragePercentage = report.totalCoveragePercentage

        val summary = "$totalCoveragePercentage% Coverage"
        val summaryRowFormatter = "%-${headerTitleSize}s"
        val summaryRow = "| ${summaryRowFormatter.format(summary)} |"

        val header: List<String> = listOf(titleSeparator, tableTitle, titleSeparator, tableHeader, headerSeparator)
        val body: List<String> = report.rows.map { it.toRowString(maxPathSize) }
        val footer: List<String> = listOf(titleSeparator, summaryRow, titleSeparator)

        val coveredAPIsTable =  (header + body + footer).joinToString(System.lineSeparator())

        val missingAPIsMessageRows:MutableList<String> = mutableListOf()
        if(report.missedAPICount > 0) {
            missingAPIsMessageRows.add("${report.missedAPICount} out of ${report.totalAPICount} APIs found missing in the specification")
        }
        return coveredAPIsTable + System.lineSeparator()  + missingAPIsMessageRows.joinToString(System.lineSeparator())
    }
}