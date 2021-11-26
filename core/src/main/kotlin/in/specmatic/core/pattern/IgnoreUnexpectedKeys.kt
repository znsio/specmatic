package `in`.specmatic.core.pattern

import `in`.specmatic.core.UnexpectedKeyCheck
import `in`.specmatic.core.UnexpectedKeyError

object IgnoreUnexpectedKeys: UnexpectedKeyCheck {
    override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): UnexpectedKeyError? = null
}