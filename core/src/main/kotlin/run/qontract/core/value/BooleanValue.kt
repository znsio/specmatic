package run.qontract.core.value

import run.qontract.core.pattern.ExactMatchPattern
import run.qontract.core.pattern.Pattern

data class BooleanValue(val booleanValue: Boolean) : Value {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = booleanValue.toString()
    override fun displayableType(): String = "boolean"
    override fun toPattern(): Pattern = ExactMatchPattern(this)

    override fun toString() = booleanValue.toString()
}

val True = BooleanValue(true)
