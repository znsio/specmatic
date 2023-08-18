package `in`.specmatic.test.reports.coverage

data class OpenApiCoverageRow(
    val method: String,
    val path: String,
    val responseStatus: String,
    val count: String,
    val coveragePercentage: Int = 0
) {
    constructor(method: String, path: String, responseStatus: Int, count: Int, coveragePercentage: Int): this(method, path, responseStatus.toString(), count.toString(), coveragePercentage)

    fun toRowString(maxPathSize: Int): String {
        val longestStatus = "coverage"
        val statusFormat = "%${longestStatus.length}s"

        val pathFormat = "%${maxPathSize}s"
        val methodFormat = "%${"method".length}s"
        val responseFormat = if (responseStatus != "0") "%${"response".length}s" else "        "
        val countFormat = "%${"count".length}s"

        val coveragePercentage = if(path.isNotEmpty()) "$coveragePercentage%" else ""

        return "| ${statusFormat.format(coveragePercentage)} | ${pathFormat.format(path)} | ${methodFormat.format(method)} | ${responseFormat.format(responseStatus)} | ${countFormat.format(count)} |"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenApiCoverageRow) return false
        return method == other.method && path == other.path && responseStatus == other.responseStatus && count == other.count && coveragePercentage == other.coveragePercentage
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + responseStatus.hashCode()
        result = 31 * result + count.hashCode()
        result = 31 * result + coveragePercentage.hashCode()
        return result
    }
}