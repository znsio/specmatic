package io.specmatic.test.reports.renderers

import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport

class CoverageReportTextRenderer: ReportRenderer<OpenAPICoverageConsoleReport> {
    override fun render(report: OpenAPICoverageConsoleReport): String {
        val maxPathSize: Int = report.rows.maxOf { it.path.length }
        val maxRemarksSize = report.rows.maxOf { it.remarks.toString().length }

        val longestCoveragePercentageValue = "coverage"
        val statusFormat = "%${longestCoveragePercentageValue.length}s"
        val pathFormat = "%-${maxPathSize}s"
        val methodFormat = "%-${"method".length}s"
        val responseStatus = "%${"response".length}s"
        val countFormat = "%${"# exercised".length}s"
        val remarksFormat = "%-${maxRemarksSize}s"

        val tableHeader =
            "| ${statusFormat.format("coverage")} | ${pathFormat.format("path")} | ${methodFormat.format("method")} | ${responseStatus.format("response")} | ${
                countFormat.format("# exercised")
            } | ${remarksFormat.format("remarks")} |"
        val headerSeparator =
            "|-${"-".repeat(longestCoveragePercentageValue.length)}-|-${"-".repeat(maxPathSize)}-|-${methodFormat.format("------")}-|-${responseStatus.format("--------")}-|-${
                countFormat.format("-----------")
            }-|-${"-".repeat(maxRemarksSize)}-|"

        val headerTitleSize = tableHeader.length - 4
        val tableTitle = "| ${"%-${headerTitleSize}s".format("API COVERAGE SUMMARY")} |"
        val titleSeparator = "|-${"-".repeat(headerTitleSize)}-|"

        val totalCoveragePercentage = report.totalCoveragePercentage

        val totalPaths = pluralisePath(report.totalEndpointsCount)

        val summary = "$totalCoveragePercentage% API Coverage reported from $totalPaths"
        val summaryRowFormatter = "%-${headerTitleSize}s"
        val summaryRow = "| ${summaryRowFormatter.format(summary)} |"

        val header: List<String> = listOf(titleSeparator, tableTitle, titleSeparator, tableHeader, headerSeparator)
        val body: List<String> = report.rows.map { it.toRowString(maxPathSize, maxRemarksSize) }
        val footer: List<String> = listOf(titleSeparator, summaryRow, titleSeparator)

        val coveredAPIsTable =  (header + body + footer).joinToString(System.lineSeparator())

        val missingAndNotImplementedAPIsMessageRows:MutableList<String> = mutableListOf()

        if(report.missedEndpointsCount > 0) {
            val missedPaths = pluralisePath(report.missedEndpointsCount)
            missingAndNotImplementedAPIsMessageRows.add("$missedPaths found in the app ${isOrAre(report.missedEndpointsCount)} not documented in the spec.")
        }

        if(report.partiallyMissedEndpointsCount > 0) {
            val partiallyMissedPaths = pluralisePath(report.partiallyMissedEndpointsCount)
            missingAndNotImplementedAPIsMessageRows.add("$partiallyMissedPaths found in the app ${isOrAre(report.partiallyMissedEndpointsCount)} partially documented in the spec.")
        }

        if(report.notImplementedAPICount > 0) {
            val notImplementedPaths = pluralisePath(report.notImplementedAPICount)
            missingAndNotImplementedAPIsMessageRows.add("$notImplementedPaths found in the spec ${isOrAre(report.notImplementedAPICount)} not implemented.")
        }

        if(report.partiallyNotImplementedAPICount > 0) {
            val partiallyNotImplementedPaths = pluralisePath(report.partiallyNotImplementedAPICount)
            missingAndNotImplementedAPIsMessageRows.add("$partiallyNotImplementedPaths found in the spec ${isOrAre(report.partiallyNotImplementedAPICount)} partially implemented.")
        }

        return coveredAPIsTable + System.lineSeparator()  + missingAndNotImplementedAPIsMessageRows.joinToString(System.lineSeparator()) + System.lineSeparator()
    }

    private fun pluralisePath(count: Int): String =
        "$count path${if (count == 1) "" else "s"}"


    private fun isOrAre(count: Int): String = if (count > 1) "are" else "is"

}