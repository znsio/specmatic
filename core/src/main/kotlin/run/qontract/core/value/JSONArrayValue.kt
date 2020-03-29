package run.qontract.core.value

import run.qontract.core.utilities.valueArrayToJsonString

data class JSONArrayValue(val list: List<Value>) : Value {
    override val value: Any = list
    override val httpContentType: String = "application/json"
    override fun toString() = valueArrayToJsonString(list)
}
