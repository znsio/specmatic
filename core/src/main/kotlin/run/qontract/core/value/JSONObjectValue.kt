package run.qontract.core.value

import run.qontract.core.pattern.JSONObjectPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.utilities.valueMapToPrettyJsonString

data class JSONObjectValue(val jsonObject: Map<String, Value> = emptyMap()) : Value {
    override val httpContentType = "application/json"

    override fun displayableValue() = toStringValue()
    override fun toStringValue() = valueMapToPrettyJsonString(jsonObject)
    override fun displayableType(): String = "json object"
    override fun toMatchingPattern(): Pattern = JSONObjectPattern(jsonObject.mapValues { it.value.toMatchingPattern() })
    override fun type(): Pattern = JSONObjectPattern()

    override fun toString() = valueMapToPrettyJsonString(jsonObject)
}
