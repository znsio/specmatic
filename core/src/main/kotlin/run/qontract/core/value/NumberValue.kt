package run.qontract.core.value

data class NumberValue(val number: Number) : Value {
    override val httpContentType = "text/plain"

    override fun toDisplayValue(): String = toStringValue()
    override fun toStringValue() = number.toString()
    override fun toString() = number.toString()
}