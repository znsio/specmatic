package `in`.specmatic.conversions

open class EnvironmentAndPropertiesConfiguration(val environmentVariables: Map<String, String>, val systemProperties: Map<String, String>) {
    constructor() : this(System.getenv(), System.getProperties().map { it.key.toString() to (it.value?.toString() ?: "") }.toMap())

    open fun getEnvironmentVariable(variableName: String): String? {
        return environmentVariables[variableName]
    }

    open fun getSystemProperty(variableName: String): String? {
        return systemProperties[variableName]
    }

    open fun getSetting(variableName: String): String? {
        return getEnvironmentVariable(variableName) ?: getSystemProperty(variableName)
    }
}
