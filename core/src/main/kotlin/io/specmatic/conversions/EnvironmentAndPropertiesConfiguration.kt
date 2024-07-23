package io.specmatic.conversions

import io.specmatic.core.SpecmaticConfig
import org.apache.commons.lang3.BooleanUtils

data class EnvironmentAndPropertiesConfiguration(
    val environmentVariables: Map<String, String>,
    val systemProperties: Map<Any?, Any?>,
    val specmaticConfig: SpecmaticConfig? = null
) {
    constructor(specmaticConfig: SpecmaticConfig? = null) : this(System.getenv(), System.getProperties().toMap(), specmaticConfig)

    companion object {
        const val SPECMATIC_GENERATIVE_TESTS = "SPECMATIC_GENERATIVE_TESTS"
        const val ONLY_POSITIVE = "ONLY_POSITIVE"
        const val VALIDATE_RESPONSE_VALUE = "VALIDATE_RESPONSE_VALUE"
        const val EXTENSIBLE_SCHEMA = "EXTENSIBLE_SCHEMA"
        const val MAX_TEST_REQUEST_COMBINATIONS = "MAX_TEST_REQUEST_COMBINATIONS"
        const val SCHEMA_EXAMPLE_DEFAULT = "SCHEMA_EXAMPLE_DEFAULT"
        const val SPECMATIC_TEST_PARALLELISM = "SPECMATIC_TEST_PARALLELISM"

        const val LOCAL_TESTS_DIRECTORY = "LOCAL_TESTS_DIRECTORY"

        fun setProperty(name: String, value: String): EnvironmentAndPropertiesConfiguration {
            return EnvironmentAndPropertiesConfiguration(mapOf(), mapOf(name to value))
        }

        fun setProperties(properties: Map<String, String>): EnvironmentAndPropertiesConfiguration {
            return EnvironmentAndPropertiesConfiguration(mapOf(), properties.mapValues { it.value })
        }

        fun booleanFlag(flagName: String) =
            BooleanUtils.toBoolean(System.getenv(flagName) ?: System.getProperty(flagName) ?: "false")

        fun flagValue(flagName: String): String? = System.getenv(flagName) ?: System.getProperty(flagName)
    }

    private fun flagValue(flagName: String): String? {
        return environmentVariables[flagName] ?: systemProperties[flagName]?.toString() ?: System.getenv(flagName) ?: System.getProperty(flagName)
    }

    fun extensibleSchema(): Boolean {
        return (specmaticConfig?.enableExtensibleSchema == true)
    }

    fun schemaExampleDefaultEnabled(): Boolean {
        return (specmaticConfig?.schemaExampleDefault == true)
    }

    fun generativeTestingEnabled(): Boolean {
        return (specmaticConfig?.enableResiliencyTests == true)
    }

    fun maxTestRequestCombinations(): Int {
        return (specmaticConfig?.maxTestRequestCombinations ?: Int.MAX_VALUE)
    }

    fun onlyPositive(): Boolean {
        return (specmaticConfig?.enableOnlyPositiveTests == true)
    }

    fun validateResponseValue(): Boolean {
        return (specmaticConfig?.enableResponseValueValidation == true)
    }

    fun testParallelism(): String? {
        return flagValue(SPECMATIC_TEST_PARALLELISM)
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
