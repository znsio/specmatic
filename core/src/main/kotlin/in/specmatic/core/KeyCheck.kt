package `in`.specmatic.core

import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.value.StringValue

class KeyCheck(val patternKeyCheck: KeyErrorCheck = CheckOnlyPatternKeys,
               var unexpectedKeyCheck: UnexpectedKeyCheck = ValidateUnexpectedKeys,
               private val overrideUnexpectedKeyCheck: OverrideUnexpectedKeyCheck? = ::overrideUnexpectedKeyCheck
) {
    fun disableOverrideUnexpectedKeycheck(): KeyCheck {
        return KeyCheck(patternKeyCheck, unexpectedKeyCheck, null)
    }

    fun withUnexpectedKeyCheck(unexpectedKeyCheck: UnexpectedKeyCheck): KeyCheck {
        return this.overrideUnexpectedKeyCheck?.invoke(this, unexpectedKeyCheck) ?: this
    }

    fun validate(
        pattern: Map<String, Any>,
        actual: Map<String, Any>
    ): KeyError? {
        return validateAll(pattern, actual).firstOrNull()
    }

    fun validateAll(
        pattern: Map<String, Any>,
        actual: Map<String, Any>
    ): List<KeyError> {
        return patternKeyCheck.validateList(pattern, actual).plus(unexpectedKeyCheck.validateList(pattern, actual))
    }

    fun validateAllCaseInsensitive(pattern: Map<String, Pattern>, actual: Map<String, StringValue>): List<KeyError> {
        return patternKeyCheck.validateListCaseInsensitive(pattern, actual).plus(unexpectedKeyCheck.validateListCaseInsensitive(pattern, actual))
    }

}

private fun overrideUnexpectedKeyCheck(keyCheck: KeyCheck, unexpectedKeyCheck: UnexpectedKeyCheck): KeyCheck {
    return KeyCheck(keyCheck.patternKeyCheck, unexpectedKeyCheck)
}

typealias OverrideUnexpectedKeyCheck = (KeyCheck, UnexpectedKeyCheck) -> KeyCheck

val DefaultKeyCheck = KeyCheck()
