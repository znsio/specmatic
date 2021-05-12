package `in`.specmatic.core.value

import io.ktor.http.quote
import io.ktor.util.InternalAPI
import org.w3c.dom.Document
import org.w3c.dom.Node
import `in`.specmatic.core.ExampleDeclarations
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.*

data class StringValue(val string: String = "") : Value, ScalarValue, XMLValue {
    override val httpContentType = "text/plain"

    @OptIn(InternalAPI::class)
    override fun displayableValue(): String = toStringValue().quote()
    override fun toStringValue() = string
    override fun displayableType(): String = "string"
    override fun exactMatchElseType(): Pattern {
        return when {
            isPatternToken() -> DeferredPattern(string)
            else -> ExactValuePattern(this)
        }
    }

    override fun build(document: Document): Node = document.createTextNode(string)

    override fun matchFailure(): Result.Failure =
        Result.Failure("Unexpected child value found: $string")

    override fun listOf(valueList: List<Value>): Value {
        return JSONArrayValue(valueList)
    }

    override fun type(): Pattern = StringPattern

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> =
            primitiveTypeDeclarationWithKey(key, types, exampleDeclarations, displayableType(), string)

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> =
            primitiveTypeDeclarationWithoutKey(exampleKey, types, exampleDeclarations, displayableType(), string)

    override fun toString() = string

    fun isPatternToken(): Boolean = isPatternToken(string.trim())
    fun trimmed(): StringValue = StringValue(string.trim())
}