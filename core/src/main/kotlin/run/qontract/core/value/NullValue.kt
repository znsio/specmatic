package run.qontract.core.value

object NullValue : Value {
    override val httpContentType: String = "text/pain"

    override fun toDisplayValue(): String = "null"
    override fun toStringValue() = ""
    override fun toString() = ""
}
