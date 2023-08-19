package `in`.specmatic.test.reports.coverage

import kotlin.math.roundToInt

data class OpenAPICoverageReport(
    val rows: List<OpenApiCoverageRow>,
    val totalEndpointsCount: Int,
    val missedEndpointsCount: Int
) {
    var totalCoveragePercentage = 0

    init {
        val coveragePercentageSum = rows.sumOf { it.coveragePercentage }
        totalCoveragePercentage = (coveragePercentageSum / totalEndpointsCount).toDouble().roundToInt()
    }
}