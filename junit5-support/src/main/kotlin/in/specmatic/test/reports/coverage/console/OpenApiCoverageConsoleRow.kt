package `in`.specmatic.test.reports.coverage.console

data class OpenApiCoverageConsoleRow(
    val method: String,
    val path: String,
    val responseStatus: String,
    val count: String,
    val coveragePercentage: Int = 0,
    val remarks: Remarks
) {
    constructor(
        method: String,
        path: String,
        responseStatus: Int,
        count: Int,
        coveragePercentage: Int,
        remarks: Remarks
    ) : this(method, path, responseStatus.toString(), count.toString(), coveragePercentage, remarks)

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

        val coveragePercentage = if (path.isNotEmpty()) "$coveragePercentage%" else ""

        return "| ${statusFormat.format(coveragePercentage)} | ${pathFormat.format(path)} | ${methodFormat.format(method)} | ${
            responseFormat.format(
                responseStatus
            )
        } | ${countFormat.format(count)} | ${remarksFormat.format(remarks.toString())} |"
    }
}

