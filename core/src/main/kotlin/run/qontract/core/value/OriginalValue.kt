package run.qontract.core.value

data class OriginalValue(override val value: Any) : Value {
    override val httpContentType = "text/plain"
    override fun toString() = value.toString()
}