package run.qontract.core.value

object NullValue : Value {
    override val value
        get() = throw Exception("Don't ask for the value of the NullValue")
    override val httpContentType: String = "text/pain"
    override fun toString() = ""
}
