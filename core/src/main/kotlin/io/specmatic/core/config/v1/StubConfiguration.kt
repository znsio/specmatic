package io.specmatic.core.config.v1

import io.specmatic.core.config.SPECMATIC_STUB_DICTIONARY
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_STUB_DELAY
import io.specmatic.core.utilities.Flags.Companion.getLongValue
import io.specmatic.core.utilities.Flags.Companion.getStringValue

data class StubConfiguration(
    val generative: Boolean? = false,
    val delayInMilliseconds: Long? = getLongValue(SPECMATIC_STUB_DELAY),
    val dictionary: String? = getStringValue(SPECMATIC_STUB_DICTIONARY),
    val includeMandatoryAndRequestedKeysInResponse: Boolean? = true
)