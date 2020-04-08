package run.qontract.core.value

object NullValue : Value {
    override val httpContentType: String = "text/pain"

    override fun displayableValue(): String = "null"
    override fun toStringValue() = ""
    override fun displayableType(): String = "null"

    override fun toString() = ""
}
