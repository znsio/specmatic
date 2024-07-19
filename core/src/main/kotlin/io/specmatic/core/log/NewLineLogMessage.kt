package io.specmatic.core.log

import io.specmatic.core.value.JSONObjectValue

object NewLineLogMessage: LogMessage {
    override fun toJSONObject(): JSONObjectValue {
        return JSONObjectValue()
    }

    override fun toLogString(): String {
        return ""
    }
}