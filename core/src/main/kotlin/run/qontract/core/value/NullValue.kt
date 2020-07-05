package run.qontract.core.value

import run.qontract.core.pattern.NullPattern
import run.qontract.core.pattern.Pattern

object NullValue : Value, ScalarValue {
    override val httpContentType: String = "text/pain"

    override fun displayableValue(): String = "null"
    override fun toStringValue() = ""
    override fun displayableType(): String = "null"
    override fun exactMatchElseType(): Pattern = NullPattern
    override fun type(): Pattern = NullPattern
    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithKey(key, types, examples, displayableType(), "(null)")

    override fun listOf(valueList: List<Value>): Value {
        return JSONArrayValue(valueList)
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithoutKey(exampleKey, types, examples, displayableType(), "(null)")

    override fun toString() = ""
}
