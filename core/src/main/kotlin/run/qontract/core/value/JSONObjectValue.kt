package run.qontract.core.value

import run.qontract.core.pattern.JSONObjectPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.utilities.valueMapToPrettyJsonString

data class JSONObjectValue(val jsonObject: Map<String, Value> = emptyMap()) : Value {
    override val httpContentType = "application/json"

    override fun displayableValue() = toStringValue()
    override fun toStringValue() = valueMapToPrettyJsonString(jsonObject)
    override fun displayableType(): String = "json object"
    override fun toPattern(): Pattern = JSONObjectPattern(jsonObject.mapValues { it.value.toPattern() })

    override fun toString() = valueMapToPrettyJsonString(jsonObject)
}
