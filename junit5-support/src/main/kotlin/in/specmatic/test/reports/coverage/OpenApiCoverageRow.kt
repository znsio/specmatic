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
        //TODO (review) the count of spaces in the string with spaces should be derived from response length
        val responseFormat = if (responseStatus != "0") "%${"response".length}s" else "        "
        val countFormat = "%${"count".length}s"

        val coveragePercentage = if(path.isNotEmpty()) "$coveragePercentage%" else ""

        return "| ${statusFormat.format(coveragePercentage)} | ${pathFormat.format(path)} | ${methodFormat.format(method)} | ${responseFormat.format(responseStatus)} | ${countFormat.format(count)} |"
    }
}