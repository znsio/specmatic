package `in`.specmatic.core

fun interface UnexpectedKeyCheck {
    fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError?
}