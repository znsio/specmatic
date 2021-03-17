package `in`.specmatic.core

import java.lang.NumberFormatException

fun toVersion(versionSpec: String) =
    versionSpec.trim().split("\\.".toRegex()).let {parts ->
        if(parts.isEmpty())
            throw Exception("Major version does not exist in $versionSpec")

        Version(mustBeANumber(parts[0], "The first part of $versionSpec is not a number."), parts.getOrNull(1)?.let { mustBeANumber(it, "The second part of $versionSpec is not a number.") } )
    }

fun mustBeANumber(value: String, errorMessage: String): String {
    return try {
        value.toInt()
        value
    } catch (e: NumberFormatException) {
        throw Exception(errorMessage)
    }
}

data class Version(val majorVersion: String, val minorVersion: String?) {
    fun toQueryParams() = majorVersionFragment() + minorVersionFragment()

    private fun majorVersionFragment() = fragment(majorVersion, "majorVersion")
    private fun minorVersionFragment() = fragment(minorVersion, "minorVersion")
    private fun fragment(value: String?, name: String) = value?.let { "&$name=$it" } ?: ""

    fun toDisplayableString(): String {
        val minorVersionString = minorVersion?.let { ".$minorVersion" } ?: ""
        return "$majorVersion$minorVersionString"
    }

    fun incrementedMinorVersion(): Version = Version(majorVersion, minorVersion?.let { (it.toInt() + 1).toString() } ?: "1")
}
