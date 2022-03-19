package `in`.specmatic.core

import `in`.specmatic.core.pattern.isOptional

internal object CheckOnlyPatternKeys: KeyErrorCheck {
    override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? {
        return validateList(pattern, actual).firstOrNull()
    }

    override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<KeyError> {
        return pattern.minus("...").keys.filter { key ->
            isMissingKey(actual, key)
        }.map { it.toMissingKeyError() }
    }
}

internal fun String.toMissingKeyError(): MissingKeyError {
    return MissingKeyError(this)
}

internal fun isMissingKey(jsonObject: Map<String, Any?>, key: String) =
    when {
        isOptional(key) -> false
        else -> key !in jsonObject && "$key?" !in jsonObject && "$key:" !in jsonObject
    }
