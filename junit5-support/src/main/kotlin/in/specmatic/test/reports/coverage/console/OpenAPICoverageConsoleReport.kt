package `in`.specmatic.test.reports.coverage.console

import kotlin.math.roundToInt

data class OpenAPICoverageConsoleReport(
    val rows: List<OpenApiCoverageConsoleRow>,
    val totalEndpointsCount: Int,
    val missedEndpointsCount: Int,
    val notImplementedAPICount: Int
) {
    val totalCoveragePercentage: Int = if (totalEndpointsCount > 0) (coveragePercentageSum(rows) / totalEndpointsCount).toDouble().roundToInt() else 0

    private fun coveragePercentageSum(rows: List<OpenApiCoverageConsoleRow>) = rows.sumOf { it.coveragePercentage }
}