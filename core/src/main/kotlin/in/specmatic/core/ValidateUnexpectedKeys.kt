package `in`.specmatic.core

import `in`.specmatic.core.pattern.withoutOptionality

interface UnexpectedKeyCheck {
    fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): UnexpectedKeyError?
}

object ValidateUnexpectedKeys: UnexpectedKeyCheck {
    override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): UnexpectedKeyError? {
        val patternKeys = pattern.minus("...").keys.map { withoutOptionality(it) }
        val actualKeys = actual.keys.map { withoutOptionality(it) }

        return actualKeys.minus(patternKeys.toSet()).firstOrNull()?.let {
            UnexpectedKeyError(it)
        }
    }
}
