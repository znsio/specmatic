package run.qontract.core.value

import io.ktor.http.quote
import io.ktor.util.InternalAPI
import run.qontract.core.pattern.*

data class StringValue(val string: String = "") : Value, ScalarValue {
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

    override fun type(): Pattern = StringPattern

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithKey(key, types, examples, displayableType(), string)

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithoutKey(exampleKey, types, examples, displayableType(), string)

    override fun toString() = string

    fun isPatternToken(): Boolean = isPatternToken(string)
}