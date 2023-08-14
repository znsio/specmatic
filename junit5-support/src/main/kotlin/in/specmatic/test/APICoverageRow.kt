package `in`.specmatic.test

data class APICoverageRow(val method: String, val path: String, val responseStatus: String, val count: String, val coverageStatus: CoverageStatus) {
    constructor(method: String, path: String, responseStatus: Int, count: Int, coverageStatus: CoverageStatus): this(method, path, responseStatus.toString(), count.toString(), coverageStatus)

    fun toRowString(maxPathSize: Int): String {
        val longestStatus = longestStatus()
        val statusFormat = "%${longestStatus.length}s"

        val pathFormat = "%${maxPathSize}s"
        val methodFormat = "%${"method".length}s"
        val responseFormat = "%${"response".length}s"
        val countFormat = "%${"count".length}s"

        val status = if(path.isNotEmpty()) coverageStatus.toString().lowercase() else ""

        return "| ${statusFormat.format(status)} | ${pathFormat.format(path)} | ${methodFormat.format(method)} | ${responseFormat.format(responseStatus)} | ${countFormat.format(count)} |"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is APICoverageRow) return false
        return method == other.method && path == other.path && responseStatus == other.responseStatus && count == other.count && coverageStatus == other.coverageStatus
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + responseStatus.hashCode()
        result = 31 * result + count.hashCode()
        result = 31 * result + coverageStatus.hashCode()
        return result
    }
}