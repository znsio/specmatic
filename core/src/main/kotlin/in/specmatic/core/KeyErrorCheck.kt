package `in`.specmatic.core

interface KeyErrorCheck {
    fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError?
    fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<KeyError>
}