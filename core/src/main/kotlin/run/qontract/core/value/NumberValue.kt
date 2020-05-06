package run.qontract.core.value

import run.qontract.core.pattern.ExactValuePattern
import run.qontract.core.pattern.NumberTypePattern
import run.qontract.core.pattern.Pattern

data class NumberValue(val number: Number) : Value {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = number.toString()
    override fun displayableType(): String = "number"
    override fun toPattern(): Pattern = ExactValuePattern(this)
    override fun type(): Pattern {
        return NumberTypePattern
    }

    override fun toString() = number.toString()
}