package run.qontract.core.value

class NoValue : Value {
    override val value: Any = ""
    override val httpContentType = "text/plain"
    override fun toString() = ""
}