package `in`.specmatic.test.reports.coverage

import kotlin.math.roundToInt

class APICoverageReport(val rows: List<APICoverageRow>, val totalEndpointsCount:Int, val missedEndpointsCount: Int) {
    var totalCoveragePercentage = 0

    init {
        val coveragePercentageSum = rows.sumOf { it.coveragePercentage }
        totalCoveragePercentage = (coveragePercentageSum / totalEndpointsCount).toDouble().roundToInt()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is APICoverageReport) return false
        return rows == other.rows && missedEndpointsCount == other.missedEndpointsCount
    }

    override fun hashCode(): Int {
        var result = rows.hashCode()
        result = 31 * result + missedEndpointsCount.hashCode()
        return result
    }
}