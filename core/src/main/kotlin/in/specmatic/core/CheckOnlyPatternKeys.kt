package `in`.specmatic.core

import `in`.specmatic.core.pattern.isMissingKey

internal object CheckOnlyPatternKeys: KeyErrorCheck {
    override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? {
        return validateList(pattern, actual).firstOrNull()
    }

    override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<KeyError> {
        return pattern.minus("...").keys.find { key ->
            isMissingKey(actual, key)
        }?.toMissingKeyError()?.let { listOf(it) } ?: emptyList()
    }
}