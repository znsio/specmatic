package run.qontract.core.value

import run.qontract.core.utilities.mapToJsonString

data class JSONObjectValue(val jsonObject: Map<String, Any?>) : Value {
    override val httpContentType = "application/json"
    override val value: Any = jsonObject

    override fun toString() = mapToJsonString(jsonObject)
}