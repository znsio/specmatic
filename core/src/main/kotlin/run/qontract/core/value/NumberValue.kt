package run.qontract.core.value

import run.qontract.core.pattern.ExactValuePattern
import run.qontract.core.pattern.NumberPattern
import run.qontract.core.pattern.Pattern

data class NumberValue(val number: Number) : Value, ScalarValue {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = number.toString()
    override fun displayableType(): String = "number"
    override fun toExactType(): Pattern = ExactValuePattern(this)
    override fun type(): Pattern = NumberPattern

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithKey(key, types, examples, displayableType(), number.toString())

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithoutKey(exampleKey, types, examples, displayableType(), number.toString())

    override fun toString() = number.toString()
}