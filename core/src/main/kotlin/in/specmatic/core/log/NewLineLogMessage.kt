package `in`.specmatic.core.log

import `in`.specmatic.core.value.JSONObjectValue

object NewLineLogMessage: LogMessage {
    override fun toJSONObject(): JSONObjectValue {
        return JSONObjectValue()
    }

    override fun toLogString(): String {
        return ""
    }
}