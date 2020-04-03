package run.qontract.core.value

data class StringValue(val string: String = "") : Value {
    override val value: Any = string
    override val httpContentType = "text/plain"

    override fun toStringValue() = string
    override fun toString() = string
}