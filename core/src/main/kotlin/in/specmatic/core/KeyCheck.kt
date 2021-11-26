package `in`.specmatic.core

class KeyCheck(val patternKeyCheck: KeyErrorCheck = CheckOnlyPatternKeys,
               var unexpectedKeyCheck: KeyErrorCheck = ValidateUnexpectedKeys,
               private val overrideUnexpectedKeyCheck: OverrideUnexpectedKeyCheck? = ::overrideUnexpectedKeyCheck
) : KeyErrorCheck {
    fun disableOverrideUnexpectedKeycheck(): KeyCheck {
        return KeyCheck(patternKeyCheck, unexpectedKeyCheck, null)
    }

    fun withUnexpectedKeyCheck(unexpectedKeyCheck: KeyErrorCheck): KeyCheck {
        return this.overrideUnexpectedKeyCheck?.invoke(this, unexpectedKeyCheck) ?: this
    }

    override fun validate(
        pattern: Map<String, Any>,
        actual: Map<String, Any>
    ): KeyError? {
        return patternKeyCheck.validate(pattern, actual) ?: unexpectedKeyCheck.validate(pattern, actual)
    }

}

private fun overrideUnexpectedKeyCheck(keyCheck: KeyCheck, _unexpectedKeyCheck: KeyErrorCheck): KeyCheck {
    return KeyCheck(keyCheck.patternKeyCheck, _unexpectedKeyCheck)
}

typealias OverrideUnexpectedKeyCheck = (KeyCheck, KeyErrorCheck) -> KeyCheck

val DefaultKeyCheck = KeyCheck()
