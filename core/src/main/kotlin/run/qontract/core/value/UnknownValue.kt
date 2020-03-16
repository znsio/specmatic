package run.qontract.core.value

class UnknownValue(override val value: Any) : Value {
    override val httpContentType = "text/plain"
}