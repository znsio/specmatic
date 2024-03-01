package `in`.specmatic.conversions

interface EnvironmentConfiguration {
    fun getEnvironmentVariable(variableName: String): String?

    fun getSystemProperty(variableName: String): String?

    fun getSetting(variableName: String): String? {
        return getEnvironmentVariable(variableName) ?: getSystemProperty(variableName)
    }
}