package `in`.specmatic.core

import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.value.StringValue

interface KeyErrorCheck {
    fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError?
    fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<KeyError>
    fun validateListCaseInsensitive(pattern: Map<String, Pattern>, actual: Map<String, StringValue>): List<KeyError>
}