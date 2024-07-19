package io.specmatic.core.log

import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

class NonVerboseExceptionLog(val e: Throwable, val msg: String?): LogMessage {
    override fun toJSONObject(): JSONObjectValue {
        val data: Map<String, Value> = mapOf(
            "className" to StringValue(e.javaClass.name),
            "cause" to StringValue(exceptionCauseMessage(e))
        )

        val message: Map<String, Value> = msg?.let {
            mapOf("message" to StringValue(msg))
        } ?: emptyMap()

        return JSONObjectValue(data.plus(message))
    }

    override fun toLogString(): String {
        return when(msg) {
            null -> exceptionCauseMessage(e)
            else -> "${msg}: ${exceptionCauseMessage(e)}"
        }
    }
}