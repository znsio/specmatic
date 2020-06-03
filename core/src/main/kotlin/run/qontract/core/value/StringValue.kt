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
    override fun typeDeclaration(typeName: String): Pair<TypeDeclaration, ExampleDeclaration> =
            Pair(TypeDeclaration("(${displayableType()})"), ExampleDeclaration(newValue = string))

    override fun toString() = string

    fun isPatternToken(): Boolean = isPatternToken(string)
}