package run.qontract.core.value

import run.qontract.core.utilities.valueArrayToJsonString

data class JSONArrayValue(val list: List<Value>) : Value {
    override val httpContentType: String = "application/json"

    override fun toDisplayValue(): String = toStringValue()
    override fun toStringValue() = valueArrayToJsonString(list)

    override fun toString() = valueArrayToJsonString(list)
}
