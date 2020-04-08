package run.qontract.core.value

data class BooleanValue(val booleanValue: Boolean) : Value {
    override val httpContentType = "text/plain"

    override fun toDisplayValue(): String = toStringValue()
    override fun toStringValue() = booleanValue.toString()

    override fun toString() = booleanValue.toString()
}

public val True = BooleanValue(true)
