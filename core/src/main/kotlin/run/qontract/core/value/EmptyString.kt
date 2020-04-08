package run.qontract.core.value

object EmptyString : Value {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = ""
    override fun toStringValue() = ""
    override fun displayableType(): String = "empty string"

    override fun toString() = ""
}