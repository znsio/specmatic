package `in`.specmatic.core

import org.apache.commons.lang3.BooleanUtils

object Flags {
    private const val customResponseName = "CUSTOM_RESPONSE"
    const val negativeTestingFlag = "SPECMATIC_GENERATIVE_TESTS"
    const val maxTestRequestCombinationsFlag = "MAX_TEST_REQUEST_COMBINATIONS"

    private fun flagValue(flagName: String): String? {
        return System.getenv(flagName) ?: System.getProperty(flagName)
    }

    fun customResponse(): Boolean {
        return flagValue(customResponseName) == "true"
    }

    fun generativeTestingEnabled(): Boolean {
        return BooleanUtils.toBoolean(flagValue(negativeTestingFlag) ?: "false")
    }

    fun maxTestRequestCombinations(): Int {
        return (flagValue(maxTestRequestCombinationsFlag) ?: "64").toInt()
    }
}