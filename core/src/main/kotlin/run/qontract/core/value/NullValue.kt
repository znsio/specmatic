package run.qontract.core.value

import run.qontract.core.pattern.NullPattern
import run.qontract.core.pattern.Pattern

object NullValue : Value {
    override val httpContentType: String = "text/pain"

    override fun displayableValue(): String = "null"
    override fun toStringValue() = ""
    override fun displayableType(): String = "null"
    override fun toExactType(): Pattern = NullPattern
    override fun type(): Pattern = NullPattern
    override fun typeDeclarationWithKey(key: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithKey(key, examples, displayableType(), "null")

    override fun typeDeclarationWithoutKey(exampleKey: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithoutKey(exampleKey, examples, displayableType(), "null")

    override fun toString() = ""
}
