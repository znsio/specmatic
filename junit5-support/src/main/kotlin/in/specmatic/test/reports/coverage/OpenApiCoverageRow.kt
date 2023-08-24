package `in`.specmatic.test.reports.coverage

data class OpenApiCoverageRow(
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

    fun toRowString(maxPathSize: Int): String {
        val longestStatus = "coverage"
        val responseCellWidthMarkerString = "response"
        val statusFormat = "%${longestStatus.length}s"

        val pathFormat = "%-${maxPathSize}s"
        val methodFormat = "%-${"method".length}s"
        val responseFormat = if (responseStatus != "0") "%${responseCellWidthMarkerString.length}s" else " ".repeat(
            responseCellWidthMarkerString.length
        )
        val countFormat = "%${"# exercised".length}s"
        val remarksFormat = "%-${Remarks.NotImplemented.toString().length}s"

        val coveragePercentage = if (path.isNotEmpty()) "$coveragePercentage%" else ""

        return "| ${statusFormat.format(coveragePercentage)} | ${pathFormat.format(path)} | ${methodFormat.format(method)} | ${
            responseFormat.format(
                responseStatus
            )
        } | ${countFormat.format(count)} | ${remarksFormat.format(remarks.toString())} |"
    }
}

enum class Remarks(val value: String) {
    Covered("covered"),
    Missed("missing in spec"),
    NotImplemented("not implemented");

    override fun toString(): String {
        return value
    }
}