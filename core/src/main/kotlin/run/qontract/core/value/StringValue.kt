package run.qontract.core.value

import io.ktor.http.quote
import io.ktor.util.InternalAPI
import run.qontract.core.pattern.isPatternToken

data class StringValue(val string: String = "") : Value {
    override val httpContentType = "text/plain"

    @InternalAPI
    override fun displayableValue(): String = toStringValue().quote()
    override fun toStringValue() = string
    override fun displayableType(): String = "string"

    override fun toString() = string

    fun isPatternToken(): Boolean = isPatternToken(string)
}