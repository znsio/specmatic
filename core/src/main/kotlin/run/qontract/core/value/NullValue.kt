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
    override fun typeDeclaration(typeName: String): Pair<TypeDeclaration, ExampleDeclaration> =
            Pair(TypeDeclaration("(${displayableType()})"), ExampleDeclaration(newValue = "null"))

    override fun toString() = ""
}
