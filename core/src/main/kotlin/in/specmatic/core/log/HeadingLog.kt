package `in`.specmatic.core.log

import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue

class HeadingLog(var msg: String): LogMessage {
    override fun toJSONObject(): JSONObjectValue {
        return JSONObjectValue(mapOf("message" to StringValue(msg)))
    }

    override fun toLogString(): String {
        return "# $msg"
    }

}