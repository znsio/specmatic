package io.specmatic.test.reports.coverage

import io.specmatic.test.report.Remarks
import io.specmatic.test.report.ReportColumn
import io.specmatic.test.report.interfaces.CoverageRow

typealias OpenApiGroupedCoverageRows = Map<String, Map<String, Map<Int, List<OpenApiCoverageRow>>>>

data class OpenApiCoverageRow (
    val method: String,
    val path: String,
    val responseStatus: String,
    override val exercisedCount: Int,
    override val coveragePercentage: Int = 0,
    override val remark: Remarks,
    val showPath: Boolean = true,
    val showMethod: Boolean = true,
): CoverageRow {
    constructor(
        method: String,
        path: String,
        responseStatus: Int,
        count: Int,
        coveragePercentage: Int,
        remarks: Remarks,
        showPath: Boolean = true,
        showMethod: Boolean = true,
    ) : this(method, path, responseStatus.toString(), count, coveragePercentage, remarks, showPath, showMethod)

    private val formattedCoveragePercentage: String
        get() = if (showPath) "$coveragePercentage%" else ""

    private val formattedPathName: String
        get() = if (showPath) path else ""

    private val formattedMethodName: String
        get() = if (showMethod) method else ""

    private val formattedResponseStatus: String
        get() = if (responseStatus.toInt() != 0) responseStatus else ""

    override fun toRowString(tableColumns: List<ReportColumn>): String {
        return tableColumns.joinToString(separator = " | ", postfix = " |", prefix = "| ") { column ->
            val value = when (column.name) {
                "coverage" -> formattedCoveragePercentage
                "path" -> formattedPathName
                "method" -> formattedMethodName
                "response" -> formattedResponseStatus
                "#exercised" -> exercisedCount
                "result" -> remark.toString()
                else -> throw Exception("Unknown column name: ${column.name}")
            }
            column.columnFormat.format(value)
        }
    }
}


fun List<OpenApiCoverageRow>.groupCoverageRows(): OpenApiGroupedCoverageRows {
    return this.groupBy { it.path }
        .mapValues { (_, rows) ->
            rows.groupBy { it.method }
                .mapValues { (_, rows) ->
                    rows.groupBy { it.responseStatus.toInt() }
                }
        }
}
