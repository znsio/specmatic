package io.specmatic.core.log

import io.specmatic.core.value.JSONObjectValue

interface LogMessage {
    fun toJSONObject(): JSONObjectValue
    fun toLogString(): String
}