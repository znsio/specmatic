package run.qontract.core.value

import run.qontract.core.pattern.ExactValuePattern
import run.qontract.core.pattern.NumberPattern
import run.qontract.core.pattern.Pattern

data class NumberValue(val number: Number) : Value {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = number.toString()
    override fun displayableType(): String = "number"
    override fun toExactType(): Pattern = ExactValuePattern(this)
    override fun type(): Pattern = NumberPattern

    override fun typeDeclaration(typeName: String): TypeDeclaration =
            TypeDeclaration("(${displayableType()})")

    override fun toString() = number.toString()
}