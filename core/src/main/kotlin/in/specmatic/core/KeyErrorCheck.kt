package `in`.specmatic.core

interface KeyErrorCheck {
    fun validate(pattern: Map<String, Any>, actual: Map<String, Any>) = validate(pattern, actual, null)
    fun validate(pattern: Map<String, Any>, actual: Map<String, Any>, unexpectedKeyCheck: UnexpectedKeyCheck?): KeyError?
}