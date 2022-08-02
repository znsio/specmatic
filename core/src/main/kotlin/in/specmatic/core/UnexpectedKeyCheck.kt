package `in`.specmatic.core

import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.value.StringValue

interface UnexpectedKeyCheck {
    fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): UnexpectedKeyError?
    fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<UnexpectedKeyError>
    fun validateListCaseInsensitive(pattern: Map<String, Pattern>, actual: Map<String, StringValue>): List<UnexpectedKeyError>
}