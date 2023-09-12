package `in`.specmatic.test.reports.coverage

import kotlin.math.roundToInt

data class OpenAPICoverageReport(
    val rows: List<OpenApiCoverageRow>,
    val totalEndpointsCount: Int,
    val missedEndpointsCount: Int,
    val notImplementedAPICount: Int
) {
    val totalCoveragePercentage: Int = if (totalEndpointsCount > 0) (coveragePercentageSum(rows) / totalEndpointsCount).toDouble().roundToInt() else 0

    private fun coveragePercentageSum(rows: List<OpenApiCoverageRow>) = rows.sumOf { it.coveragePercentage }
}