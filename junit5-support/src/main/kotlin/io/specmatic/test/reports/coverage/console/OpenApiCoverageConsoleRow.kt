package io.specmatic.test.reports.coverage.console

data class OpenApiCoverageConsoleRow(
    val method: String,
    val path: String,
    val responseStatus: String,
    val count: String,
    val coveragePercentage: Int = 0,
    val remarks: Remarks,
    val showPath: Boolean = true,
    val showMethod: Boolean = true,
) {
    constructor(
        method: String,
        path: String,
        responseStatus: Int,
        count: Int,
        coveragePercentage: Int,
        remarks: Remarks,
        showPath: Boolean = true,
        showMethod: Boolean = true,
    ) : this(method, path, responseStatus.toString(), count.toString(), coveragePercentage, remarks, showPath, showMethod)

    fun toRowString(maxPathSize: Int, maxRemarksSize: Int): String {
        val longestStatus = "coverage"
        val responseCellWidthMarkerString = "response"
        val statusFormat = "%${longestStatus.length}s"

        val pathFormat = "%-${maxPathSize}s"
        val methodFormat = "%-${"method".length}s"
        val responseFormat = if (responseStatus != "0") "%${responseCellWidthMarkerString.length}s" else " ".repeat(
            responseCellWidthMarkerString.length
        )
        val countFormat = "%${"# exercised".length}s"
        val remarksFormat = "%-${maxRemarksSize}s"

        val pathText = if (showPath) path else ""
        val methodText = if (showMethod) method else ""
        val coveragePercentage = if (showPath) "$coveragePercentage%" else ""

        return "| ${statusFormat.format(coveragePercentage)} | ${pathFormat.format(pathText)} | ${methodFormat.format(methodText)} | ${
            responseFormat.format(
                responseStatus
            )
        } | ${countFormat.format(count)} | ${remarksFormat.format(remarks.toString())} |"
    }
}

