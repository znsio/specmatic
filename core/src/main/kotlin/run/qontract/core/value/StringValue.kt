package run.qontract.core.value

import io.ktor.http.quote
import io.ktor.util.InternalAPI
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.ExactMatchPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.isPatternToken

data class StringValue(val string: String = "") : Value {
    override val httpContentType = "text/plain"

    @InternalAPI
    override fun displayableValue(): String = toStringValue().quote()
    override fun toStringValue() = string
    override fun displayableType(): String = "string"
    override fun toPattern(): Pattern {
        return if(isPatternToken())
            DeferredPattern(string)
        else
            ExactMatchPattern(this)
    }

    override fun toString() = string

    fun isPatternToken(): Boolean = isPatternToken(string)
}