package io.specmatic.test.reports.coverage.console

interface CoverageRow {
    fun toRowString(tableColumns: List<ReportColumn>): String
}
