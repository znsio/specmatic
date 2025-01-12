package io.specmatic.core.config

import io.specmatic.core.utilities.Flags.Companion.EXTENSIBLE_SCHEMA
import io.specmatic.core.utilities.Flags.Companion.ONLY_POSITIVE
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_GENERATIVE_TESTS
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_TEST_TIMEOUT
import io.specmatic.core.utilities.Flags.Companion.VALIDATE_RESPONSE_VALUE
import io.specmatic.core.utilities.Flags.Companion.getBooleanValue
import io.specmatic.core.utilities.Flags.Companion.getLongValue

data class TestConfiguration(
    val resiliencyTests: ResiliencyTestsConfig? = ResiliencyTestsConfig(
        isResiliencyTestFlagEnabled = getBooleanValue(SPECMATIC_GENERATIVE_TESTS),
        isOnlyPositiveFlagEnabled = getBooleanValue(ONLY_POSITIVE)
    ),
    val validateResponseValues: Boolean? = getBooleanValue(VALIDATE_RESPONSE_VALUE),
    val allowExtensibleSchema: Boolean? = getBooleanValue(EXTENSIBLE_SCHEMA),
    val timeoutInMilliseconds: Long? = getLongValue(SPECMATIC_TEST_TIMEOUT)
)

enum class ResiliencyTestSuite {
    all, positiveOnly, none
}

data class ResiliencyTestsConfig(
    val enable: ResiliencyTestSuite = ResiliencyTestSuite.none
) {
    constructor(isResiliencyTestFlagEnabled: Boolean, isOnlyPositiveFlagEnabled: Boolean) : this(
        enable = getEnableFrom(isResiliencyTestFlagEnabled, isOnlyPositiveFlagEnabled)
    )

    companion object {
        private fun getEnableFrom(isResiliencyTestFlagEnabled: Boolean, isOnlyPositiveFlagEnabled: Boolean): ResiliencyTestSuite {
            return when {
                isResiliencyTestFlagEnabled -> ResiliencyTestSuite.all
                isOnlyPositiveFlagEnabled -> ResiliencyTestSuite.positiveOnly
                else -> ResiliencyTestSuite.none
            }
        }
    }
}