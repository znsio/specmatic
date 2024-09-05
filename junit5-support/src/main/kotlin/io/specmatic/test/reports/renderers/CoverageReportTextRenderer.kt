package io.specmatic.test.reports.renderers

import io.specmatic.core.SpecmaticConfig
import io.specmatic.test.reports.coverage.console.ConsoleReport
import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.coverage.console.ReportColumn

class CoverageReportTextRenderer: ReportRenderer<OpenAPICoverageConsoleReport> {
    override fun render(report: OpenAPICoverageConsoleReport, specmaticConfig: SpecmaticConfig): String {
        val textReportGenerator = ConsoleReport(report.coverageRows, makeReportColumns(report), makeFooter(report))
        val coveredAPIsTable = "${System.lineSeparator()}${textReportGenerator.generate()}"

        val statistics = report.statistics
        val missingAndNotImplementedAPIsMessageRows:MutableList<String> = mutableListOf()

        if(statistics.missedEndpointsCount > 0) {
            val missedPaths = pluralisePath(statistics.missedEndpointsCount)
            missingAndNotImplementedAPIsMessageRows.add("$missedPaths found in the app ${isOrAre(statistics.missedEndpointsCount)} not documented in the spec.")
        }

        if(statistics.partiallyMissedEndpointsCount > 0) {
            val partiallyMissedPaths = pluralisePath(statistics.partiallyMissedEndpointsCount)
            missingAndNotImplementedAPIsMessageRows.add("$partiallyMissedPaths found in the app ${isOrAre(statistics.partiallyMissedEndpointsCount)} partially documented in the spec.")
        }

        if(statistics.notImplementedAPICount > 0) {
            val notImplementedPaths = pluralisePath(statistics.notImplementedAPICount)
            missingAndNotImplementedAPIsMessageRows.add("$notImplementedPaths found in the spec ${isOrAre(statistics.notImplementedAPICount)} not implemented.")
        }

        if(statistics.partiallyNotImplementedAPICount > 0) {
            val partiallyNotImplementedPaths = pluralisePath(statistics.partiallyNotImplementedAPICount)
            missingAndNotImplementedAPIsMessageRows.add("$partiallyNotImplementedPaths found in the spec ${isOrAre(statistics.partiallyNotImplementedAPICount)} partially implemented.")
        }

        return coveredAPIsTable + System.lineSeparator()  + missingAndNotImplementedAPIsMessageRows.joinToString(System.lineSeparator()) + System.lineSeparator()
    }

    private fun pluralisePath(count: Int): String =
        "$count path${if (count == 1) "" else "s"}"

    private fun isOrAre(count: Int): String = if (count > 1) "are" else "is"

    private fun makeReportColumns(report: OpenAPICoverageConsoleReport): List<ReportColumn> {
        val maxCoveragePercentageLength = "coverage".length
        val maxPathLength = report.coverageRows.maxOf { it.path.length }
        val maxMethodLength = report.coverageRows.maxOf { it.method.length }
        val maxStatusLength = report.coverageRows.maxOf { it.responseStatus.length }
        val maxExercisedLength = "#exercised".length
        val maxRemarkLength = report.coverageRows.maxOf { it.remarks.toString().length }

        return listOf(
            ReportColumn("coverage", maxCoveragePercentageLength),
            ReportColumn("path", maxPathLength),
            ReportColumn("method", maxMethodLength),
            ReportColumn("response", maxStatusLength),
            ReportColumn("#exercised", maxExercisedLength),
            ReportColumn("result", maxRemarkLength)
        )
    }

    private fun makeFooter(report: OpenAPICoverageConsoleReport): String {
        return "${report.totalCoveragePercentage}% API Coverage reported from ${report.statistics.totalEndpointsCount} Paths"
    }

}