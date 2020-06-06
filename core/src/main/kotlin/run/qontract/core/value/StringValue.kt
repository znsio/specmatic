package run.qontract.core.value

import io.ktor.http.quote
import io.ktor.util.InternalAPI
import run.qontract.core.pattern.*

data class StringValue(val string: String = "") : Value {
    override val httpContentType = "text/plain"

    @OptIn(InternalAPI::class)
    override fun displayableValue(): String = toStringValue().quote()
    override fun toStringValue() = string
    override fun displayableType(): String = "string"
    override fun toExactType(): Pattern {
        return when {
            isPatternToken() -> DeferredPattern(string)
            else -> ExactValuePattern(this)
        }
    }

    override fun type(): Pattern = StringPattern
    override fun typeDeclarationWithKey(key: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithKey(key, examples, displayableType(), string)

    override fun typeDeclarationWithoutKey(exampleKey: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            primitiveTypeDeclarationWithoutKey(exampleKey, examples, displayableType(), string)

    override fun toString() = string

    fun isPatternToken(): Boolean = isPatternToken(string)
}