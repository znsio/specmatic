package run.qontract.core.value

import run.qontract.core.pattern.isPatternToken

data class StringValue(val string: String = "") : Value {
    override val value: Any = string
    override val httpContentType = "text/plain"

    override fun toStringValue() = string
    override fun toString() = string

    fun isPatternToken(): Boolean = isPatternToken(string)
}