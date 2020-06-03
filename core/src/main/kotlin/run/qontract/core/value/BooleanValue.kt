package run.qontract.core.value

import run.qontract.core.pattern.BooleanPattern
import run.qontract.core.pattern.ExactValuePattern
import run.qontract.core.pattern.Pattern

data class BooleanValue(val booleanValue: Boolean) : Value {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = booleanValue.toString()
    override fun displayableType(): String = "boolean"
    override fun toExactType(): Pattern = ExactValuePattern(this)
    override fun type(): Pattern = BooleanPattern

    override fun typeDeclaration(typeName: String): Pair<TypeDeclaration, ExampleDeclaration> =
            Pair(TypeDeclaration("(${displayableType()})"), ExampleDeclaration(newValue = booleanValue.toString()))

    override fun toString() = booleanValue.toString()
}

val True = BooleanValue(true)
