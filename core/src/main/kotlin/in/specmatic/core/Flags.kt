package `in`.specmatic.core

import org.apache.commons.lang3.BooleanUtils

object Flags {
    const val VALIDATE_RESPONSE = "VALIDATE_RESPONSE"
    private const val CUSTOM_RESPONSE_NAME = "CUSTOM_RESPONSE"
    const val SPECMATIC_GENERATIVE_TESTS = "SPECMATIC_GENERATIVE_TESTS"
    private const val MAX_TEST_REQUEST_COMBINATIONS = "MAX_TEST_REQUEST_COMBINATIONS"
    const val SCHEMA_EXAMPLE_DEFAULT = "SCHEMA_EXAMPLE_DEFAULT"
    const val ONLY_POSITIVE = "ONLY_POSITIVE"
    const val SPECMATIC_TEST_PARALLELISM = "SPECMATIC_TEST_PARALLELISM"

    const val EXTENDABLE_SCHEMA = "EXTENDABLE_SCHEMA"

    private fun flagValue(flagName: String): String? {
        return System.getenv(flagName) ?: System.getProperty(flagName)
    }

    fun customResponse(): Boolean {
        return flagValue(CUSTOM_RESPONSE_NAME) == "true"
    }

    private fun booleanFlag(flagName: String, default: String = "false") = BooleanUtils.toBoolean(flagValue(flagName) ?: default)

    fun extendableSchemaEnabled(): Boolean {
        return booleanFlag(EXTENDABLE_SCHEMA)
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

    fun validateResponse(): Boolean {
        return booleanFlag(VALIDATE_RESPONSE, "false")
    }
}