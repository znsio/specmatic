package `in`.specmatic.core.value

import `in`.specmatic.core.ExampleDeclarations
import `in`.specmatic.core.pattern.ExactValuePattern
import `in`.specmatic.core.pattern.NumberPattern
import `in`.specmatic.core.pattern.Pattern

data class NumberValue(val number: Number) : Value, ScalarValue {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = toStringLiteral()
    override fun toStringLiteral() = number.toString()
    override fun displayableType(): String = "number"
    override fun exactMatchElseType(): Pattern = ExactValuePattern(this)
    override fun type(): Pattern = NumberPattern()

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> =
            primitiveTypeDeclarationWithKey(key, types, exampleDeclarations, displayableType(), number.toString())

    override fun listOf(valueList: List<Value>): Value {
        return JSONArrayValue(valueList)
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> =
            primitiveTypeDeclarationWithoutKey(exampleKey, types, exampleDeclarations, displayableType(), number.toString())

    override fun toString() = number.toString()
}