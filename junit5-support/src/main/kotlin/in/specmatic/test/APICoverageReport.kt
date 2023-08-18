package `in`.specmatic.test

import kotlin.math.roundToInt

class APICoverageReport(val rows: List<APICoverageRow>, val totalAPICount:Int, val missedAPICount: Int) {
    var totalCoveragePercentage = 0

    init {
        val coveragePercentageSum = rows.sumOf { it.coveragePercentage }
        totalCoveragePercentage = (coveragePercentageSum / totalAPICount).toDouble().roundToInt()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is APICoverageReport) return false
        return rows == other.rows && missedAPICount == other.missedAPICount
    }

    override fun hashCode(): Int {
        var result = rows.hashCode()
        result = 31 * result + missedAPICount.hashCode()
        return result
    }
}