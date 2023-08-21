package `in`.specmatic.test.reports.coverage

import kotlin.math.roundToInt

data class OpenAPICoverageReport(
    val rows: List<OpenApiCoverageRow>,
    val totalEndpointsCount: Int,
    val missedEndpointsCount: Int
) {
    val totalCoveragePercentage: Int = (coveragePercentageSum(rows) / totalEndpointsCount).toDouble().roundToInt()

    private fun coveragePercentageSum(rows: List<OpenApiCoverageRow>) = rows.sumOf { it.coveragePercentage }
}