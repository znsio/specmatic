package run.qontract.core.value

import run.qontract.core.utilities.arrayToJsonString

data class JSONArrayValue(val list: List<Any?>) : Value {
    override val value: Any = list
    override val httpContentType = "application/json"
    override fun toString() = arrayToJsonString(list)
}