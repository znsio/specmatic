package `in`.specmatic.conversions

interface Environment {
    fun getEnvironmentVariable(variableName: String): String?
}