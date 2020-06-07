package run.qontract.core.value

import run.qontract.core.pattern.BooleanPattern
import run.qontract.core.pattern.ExactValuePattern
import run.qontract.core.pattern.Pattern

data class BooleanValue(val booleanValue: Boolean) : Value, ScalarValue {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = booleanValue.toString()
    override fun displayableType(): String = "boolean"
    override fun toExactType(): Pattern = ExactValuePattern(this)
    override fun type(): Pattern = BooleanPattern

    override fun typeDeclarationWithKey(key: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithKey(key, examples, displayableType(), booleanValue.toString())

    override fun typeDeclarationWithoutKey(exampleKey: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithoutKey(exampleKey, examples, displayableType(), booleanValue.toString())

    override fun toString() = booleanValue.toString()
}

val True = BooleanValue(true)
