package io.specmatic.core.log

import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue

class HeadingLog(var msg: String): LogMessage {
    override fun toJSONObject(): JSONObjectValue {
        return JSONObjectValue(mapOf("message" to StringValue(msg)))
    }

    override fun toLogString(): String {
        return "# $msg"
    }

}