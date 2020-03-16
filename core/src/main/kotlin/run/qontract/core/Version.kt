package run.qontract.core

fun toVersion(versionSpec: String?) =
    when(versionSpec) {
        null -> Version(null, null)
        else -> versionSpec.trim().split("\\.".toRegex()).let {parts ->
            Version(parts.getOrNull(0), parts.getOrNull(1))
        }
    }

data class Version(val majorVersion: String?, val minorVersion: String?) {
    fun toQueryParams() = majorVersionFragment() + minorVersionFragment()

    private fun majorVersionFragment() = fragment(majorVersion, "majorVersion")
    private fun minorVersionFragment() = fragment(minorVersion, "minorVersion")
    private fun fragment(value: String?, name: String) = value?.let { "&$name=$it" } ?: ""
}