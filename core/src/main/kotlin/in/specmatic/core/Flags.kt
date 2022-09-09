package `in`.specmatic.core

import org.apache.commons.lang3.BooleanUtils

object Flags {
    private const val customResponseName = "CUSTOM_RESPONSE"
    const val negativeTestingFlag = "SPECMATIC_GENERATIVE_TESTS"

    fun customResponse(): Boolean {
        return System.getenv(customResponseName) == "true" || System.getProperty(customResponseName) == "true"
    }

    fun negativeTestingEnabled(): Boolean {
        return BooleanUtils.toBoolean(
            System.getenv(negativeTestingFlag) ?: System.getProperty(negativeTestingFlag) ?: "false"
        )
    }
}