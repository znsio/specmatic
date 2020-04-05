package run.qontract.core.value

import run.qontract.core.utilities.valueMapToPrettyJsonString

data class JSONObjectValue(val jsonObject: Map<String, Value> = emptyMap()) : Value {
    override val httpContentType = "application/json"

    override fun toDisplayValue() = toStringValue()
    override fun toStringValue() = valueMapToPrettyJsonString(jsonObject)
    override fun toString() = valueMapToPrettyJsonString(jsonObject)
}
