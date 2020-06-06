package run.qontract.core.value

import run.qontract.core.pattern.Pattern

interface Value {
    val httpContentType: String

    fun displayableValue(): String
    fun toStringValue(): String
    fun displayableType(): String
    fun toExactType(): Pattern
    fun type(): Pattern
    fun typeDeclarationWithoutKey(exampleKey: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration>
    fun typeDeclarationWithKey(key: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration>
}
