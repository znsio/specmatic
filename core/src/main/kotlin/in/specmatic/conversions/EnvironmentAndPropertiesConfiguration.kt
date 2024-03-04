package `in`.specmatic.conversions

class EnvironmentAndPropertiesConfiguration(val environmentVariables: Map<String, String>, val systemProperties: Map<String, String>) {
    constructor() : this(System.getenv(), System.getProperties().map { it.key.toString() to (it.value?.toString() ?: "") }.toMap())

    fun getEnvironmentVariable(variableName: String): String? {
        return environmentVariables[variableName]
    }

    fun getSystemProperty(variableName: String): String? {
        return systemProperties[variableName]
    }

    fun getSetting(variableName: String): String? {
        return getEnvironmentVariable(variableName) ?: getSystemProperty(variableName)
    }
}
