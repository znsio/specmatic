package run.qontract.core.value

import run.qontract.core.pattern.Pattern

interface Value {
    val httpContentType: String

    fun displayableValue(): String
    fun toStringValue(): String
    fun displayableType(): String
    fun toExactType(): Pattern
    fun type(): Pattern
    fun typeDeclaration(typeName: String): Pair<TypeDeclaration, ExampleDeclaration>
}
