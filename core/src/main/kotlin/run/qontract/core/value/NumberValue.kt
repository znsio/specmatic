package run.qontract.core.value

data class NumberValue(private val number: Number) : Value {
    override val value: Any = number
    override val httpContentType = "text/plain"
    override fun toString() = number.toString()
}