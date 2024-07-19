package io.specmatic.conversions

import org.apache.commons.lang3.BooleanUtils

data class EnvironmentAndPropertiesConfiguration(val environmentVariables: Map<String, String>, val systemProperties: Map<Any?, Any?>) {
    constructor() : this(System.getenv(), System.getProperties().toMap())

    val VALIDATE_RESPONSE_VALUE = "VALIDATE_RESPONSE_VALUE"
    val SPECMATIC_GENERATIVE_TESTS = "SPECMATIC_GENERATIVE_TESTS"
    private val MAX_TEST_REQUEST_COMBINATIONS = "MAX_TEST_REQUEST_COMBINATIONS"
    val SCHEMA_EXAMPLE_DEFAULT = "SCHEMA_EXAMPLE_DEFAULT"
    val ONLY_POSITIVE = "ONLY_POSITIVE"
    val SPECMATIC_TEST_PARALLELISM = "SPECMATIC_TEST_PARALLELISM"
    val LOCAL_TESTS_DIRECTORY = "LOCAL_TESTS_DIRECTORY"

    val EXTENSIBLE_SCHEMA = "EXTENSIBLE_SCHEMA"

    companion object {
        fun setProperty(name: String, value: String): EnvironmentAndPropertiesConfiguration {
            return EnvironmentAndPropertiesConfiguration(mapOf(), mapOf(name to value))
        }

        fun setProperties(properties: Map<String, String>): EnvironmentAndPropertiesConfiguration {
            return EnvironmentAndPropertiesConfiguration(mapOf(), properties.mapValues { it.value })
        }
    }

    private fun flagValue(flagName: String): String? {
        return environmentVariables[flagName] ?: systemProperties[flagName]?.toString() ?: System.getenv(flagName) ?: System.getProperty(flagName)
    }

    private fun booleanFlag(flagName: String, default: String = "false") = BooleanUtils.toBoolean(flagValue(flagName) ?: default)

    fun extensibleSchema(): Boolean {
        return booleanFlag(EXTENSIBLE_SCHEMA)
    }

    fun schemaExampleDefaultEnabled(): Boolean {
        return booleanFlag(SCHEMA_EXAMPLE_DEFAULT)
    }

    fun generativeTestingEnabled(): Boolean {
        return booleanFlag(SPECMATIC_GENERATIVE_TESTS)
    }

    fun maxTestRequestCombinations(): Int {
        return flagValue(MAX_TEST_REQUEST_COMBINATIONS)?.toInt() ?: Int.MAX_VALUE
    }

    fun onlyPositive(): Boolean {
        return booleanFlag(ONLY_POSITIVE)
    }

    fun testParallelism(): String? {
        return flagValue(SPECMATIC_TEST_PARALLELISM)
    }

    fun validateResponseValue(): Boolean {
        return booleanFlag(VALIDATE_RESPONSE_VALUE, "false")
    }

    fun getCachedEnvironmentVariable(variableName: String): String? {
        return environmentVariables[variableName]
    }

    fun getCachedSystemProperty(variableName: String): String? {
        return systemProperties[variableName]?.toString()
    }

    fun getCachedSetting(variableName: String): String? {
        return getCachedEnvironmentVariable(variableName) ?: getCachedSystemProperty(variableName)
    }

    fun localTestsDirectory(): String? {
        return getCachedSetting(LOCAL_TESTS_DIRECTORY)
    }

}
