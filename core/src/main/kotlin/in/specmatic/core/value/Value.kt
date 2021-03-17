package `in`.specmatic.core.value

import `in`.specmatic.core.ExampleDeclarations
import `in`.specmatic.core.pattern.Pattern

interface Value {
    val httpContentType: String

    fun displayableValue(): String
    fun toStringValue(): String
    fun displayableType(): String
    fun exactMatchElseType(): Pattern
    fun type(): Pattern
    fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations>
    fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations>
    fun listOf(valueList: List<Value>): Value
}
