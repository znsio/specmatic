package `in`.specmatic.core

interface KeyErrorCheck {
    fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError?
}