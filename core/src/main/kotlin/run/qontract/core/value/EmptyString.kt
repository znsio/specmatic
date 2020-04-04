package run.qontract.core.value

object EmptyString : Value {
    override val value: Any = ""
    override val httpContentType = "text/plain"
    override fun toStringValue() = ""
    override fun toString() = ""
}