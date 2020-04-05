package run.qontract.core.value

interface Value {
    val httpContentType: String

    fun toDisplayValue(): String
    fun toStringValue(): String
}