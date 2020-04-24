package run.qontract.core.value

import run.qontract.core.pattern.JSONArrayPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.utilities.valueArrayToJsonString

data class JSONArrayValue(val list: List<Value>) : Value {
    override val httpContentType: String = "application/json"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = valueArrayToJsonString(list)
    override fun displayableType(): String = "json array"
    override fun toPattern(): Pattern = JSONArrayPattern(list.map { it.toPattern() })

    override fun toString() = valueArrayToJsonString(list)
}
