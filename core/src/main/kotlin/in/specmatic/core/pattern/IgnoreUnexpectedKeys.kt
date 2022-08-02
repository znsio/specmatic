package `in`.specmatic.core.pattern

import `in`.specmatic.core.UnexpectedKeyCheck
import `in`.specmatic.core.UnexpectedKeyError
import `in`.specmatic.core.value.StringValue

object IgnoreUnexpectedKeys: UnexpectedKeyCheck {
    override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): UnexpectedKeyError? = null
    override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<UnexpectedKeyError> {
        return emptyList()
    }

    override fun validateListCaseInsensitive(
        pattern: Map<String, Pattern>,
        actual: Map<String, StringValue>
    ): List<UnexpectedKeyError> {
        return emptyList()
    }
}