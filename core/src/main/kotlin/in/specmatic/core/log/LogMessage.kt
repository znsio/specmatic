package `in`.specmatic.core.log

import `in`.specmatic.core.value.JSONObjectValue

interface LogMessage {
    fun toJSONObject(): JSONObjectValue
    fun toLogString(): String
}