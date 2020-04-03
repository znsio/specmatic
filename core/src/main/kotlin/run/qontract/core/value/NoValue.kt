package run.qontract.core.value

object NoValue : Value {
    override val value: Any = ""
    override val httpContentType = "text/plain"
    override fun toStringValue() = ""
    override fun toString() = ""
}