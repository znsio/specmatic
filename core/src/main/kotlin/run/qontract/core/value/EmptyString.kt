package run.qontract.core.value

object EmptyString : Value {
    override val httpContentType = "text/plain"

    override fun toDisplayValue(): String = ""
    override fun toStringValue() = ""
    override fun toString() = ""
}