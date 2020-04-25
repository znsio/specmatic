package run.qontract.core.value

import run.qontract.core.pattern.Pattern

interface Value {
    val httpContentType: String

    fun displayableValue(): String
    fun toStringValue(): String
    fun displayableType(): String
    fun toMatchingPattern(): Pattern
    fun type(): Pattern
}