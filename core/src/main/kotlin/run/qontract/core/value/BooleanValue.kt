package run.qontract.core.value

data class BooleanValue(val booleanValue: Boolean) : Value {
    override val value = booleanValue
    override val httpContentType = "text/plain"
    override fun toStringValue() = booleanValue.toString()

    override fun toString() = booleanValue.toString()
}
