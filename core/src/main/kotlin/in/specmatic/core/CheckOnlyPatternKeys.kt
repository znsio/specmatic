package `in`.specmatic.core

import `in`.specmatic.core.pattern.IgnoreUnexpectedKeys
import `in`.specmatic.core.pattern.isMissingKey

internal object CheckOnlyPatternKeys: KeyErrorCheck {
    override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>, unexpectedKeyCheck: UnexpectedKeyCheck?): KeyError? {
        return pattern.minus("...").keys.find { key ->
            isMissingKey(actual, key)
        }?.let {
            MissingKeyError(it)
        } ?: (unexpectedKeyCheck ?: IgnoreUnexpectedKeys).validate(pattern, actual)
    }
}