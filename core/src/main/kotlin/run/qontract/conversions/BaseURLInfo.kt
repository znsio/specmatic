package run.qontract.conversions

data class BaseURLInfo(val host: String, val port: Int, val scheme: String, val originalBaseURL: String)

fun toFragment(baseURLInfo: BaseURLInfo): String {
    val port: String = if(baseURLInfo.port > 0) ":${baseURLInfo.port}" else ""
    return "${baseURLInfo.host}$port"
}
