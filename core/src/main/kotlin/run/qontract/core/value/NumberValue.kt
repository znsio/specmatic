package run.qontract.core.value

data class NumberValue(val number: Number) : Value {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = number.toString()
    override fun displayableType(): String = "number"

    override fun toString() = number.toString()
}