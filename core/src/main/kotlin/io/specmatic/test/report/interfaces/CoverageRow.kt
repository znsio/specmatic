package io.specmatic.test.report.interfaces

import io.specmatic.test.report.Remarks
import io.specmatic.test.report.ReportColumn

interface CoverageRow {
    val exercisedCount: Int
    val coveragePercentage: Int
    val remark: Remarks

    fun toRowString(tableColumns: List<ReportColumn>): String
}
