package run.qontract.core.value

data class BooleanValue(val booleanValue: Boolean) : Value {
    override val httpContentType = "text/plain"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = booleanValue.toString()
    override fun displayableType(): String = "boolean"

    override fun toString() = booleanValue.toString()
}

val True = BooleanValue(true)
