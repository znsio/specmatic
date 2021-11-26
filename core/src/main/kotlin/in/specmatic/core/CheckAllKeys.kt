package `in`.specmatic.core

import `in`.specmatic.core.pattern.isMissingKey

internal object CheckAllKeys: KeyErrorCheck {
    override fun validate(
        pattern: Map<String, Any>,
        actual: Map<String, Any>
    ): KeyError? {
        return pattern.minus("...").keys.find { key ->
            isMissingKey(actual, key)
        }?.let {
            MissingKeyError(it)
        } ?: ValidateUnexpectedKeys.validate(pattern, actual)
    }
}
