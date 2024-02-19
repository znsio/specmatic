package `in`.specmatic.test.reports.coverage.console

import kotlin.math.roundToInt

data class OpenAPICoverageConsoleReport(
    val rows: List<OpenApiCoverageConsoleRow>,
    val totalEndpointsCount: Int,
    val missedEndpointsCount: Int,
    val notImplementedAPICount: Int,
    val partiallyMissedEndpointsCount: Int,
    val partiallyNotImplementedAPICount: Int
) {
    val totalCoveragePercentage: Int = calculateTotalCoveragePercentage()

    private fun calculateTotalCoveragePercentage(): Int {
        if (totalEndpointsCount == 0) return 0

        val totalCountOfEndpointsWithResponseStatuses = rows.count()
        val totalCountOfCoveredEndpointsWithResponseStatuses = rows.count { it.remarks == Remarks.Covered }

        return ((totalCountOfCoveredEndpointsWithResponseStatuses * 100) / totalCountOfEndpointsWithResponseStatuses).toDouble()
            .roundToInt()
    }
}