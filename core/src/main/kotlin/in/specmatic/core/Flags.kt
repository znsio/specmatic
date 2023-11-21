package `in`.specmatic.core

import org.apache.commons.lang3.BooleanUtils

object Flags {
    private const val customResponseName = "CUSTOM_RESPONSE"
    const val negativeTestingFlag = "SPECMATIC_GENERATIVE_TESTS"
    const val maxTestRequestCombinationsFlag = "MAX_TEST_REQUEST_COMBINATIONS"
    const val schemaExampleDefault = "SCHEMA_EXAMPLE_DEFAULT"
    const val generateOnlyFromFirst = "GENERATE_ONLY_FROM_FIRST"
    const val onlyPositive = "ONLY_POSITIVE"

    private fun flagValue(flagName: String): String? {
        return System.getenv(flagName) ?: System.getProperty(flagName)
    }

    fun customResponse(): Boolean {
        return flagValue(customResponseName) == "true"
    }

    fun booleanFlag(flagName: String, default: String = "false") = BooleanUtils.toBoolean(flagValue(flagName) ?: default)

    fun schemaExampleDefaultEnabled(): Boolean {
        return booleanFlag(schemaExampleDefault)
    }

    fun generativeTestingEnabled(): Boolean {
        return booleanFlag(negativeTestingFlag)
    }

    fun maxTestRequestCombinations(): Int {
        return flagValue(maxTestRequestCombinationsFlag)?.toInt() ?: Int.MAX_VALUE
    }

    fun generateOnlyFromFirst(): Boolean {
        return booleanFlag(generateOnlyFromFirst)
    }

    fun onlyPositive(): Boolean {
        return booleanFlag(onlyPositive)
    }
}