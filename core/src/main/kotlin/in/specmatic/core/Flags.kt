package `in`.specmatic.core

import org.apache.commons.lang3.BooleanUtils

object Flags {
    private const val customResponseName = "CUSTOM_RESPONSE"

    fun customResponse(): Boolean {
        return System.getenv(customResponseName) == "true" || System.getProperty(customResponseName) == "true"
    }

    fun enableNegativeTesting() = BooleanUtils.toBoolean(
        System.getenv("ENABLE_NEGATIVE_TESTING") ?: System.getProperty("ENABLE_NEGATIVE_TESTING") ?: "false"
    )
}