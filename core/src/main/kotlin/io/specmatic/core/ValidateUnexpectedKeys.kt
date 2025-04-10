package io.specmatic.core

import io.specmatic.core.pattern.Pattern
import io.specmatic.core.pattern.withoutOptionality
import io.specmatic.core.value.StringValue
import io.specmatic.test.asserts.ASSERT_KEYS

object ValidateUnexpectedKeys: UnexpectedKeyCheck {
    override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): UnexpectedKeyError? {
        return validateList(pattern, actual).firstOrNull()
    }

    override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<UnexpectedKeyError> {
        if (pattern.containsKey("(string)?")) {
            // Supports free-form dictionaries
            return listOf()
        }
        val patternKeys = pattern.minus("...").keys.map { withoutOptionality(it) }
        val actualKeys = actual.keys.map { withoutOptionality(it) }

        return actualKeys.minus(patternKeys.toSet().plus(ASSERT_KEYS)).map {
            UnexpectedKeyError(it)
        }
    }

    override fun validateListCaseInsensitive(
        pattern: Map<String, Pattern>,
        actual: Map<String, StringValue>
    ): List<UnexpectedKeyError> {
        val patternKeys = pattern.minus("...").keys.map { withoutOptionality(it).lowercase() }.toSet()
        val actualKeys = actual.keys.map { withoutOptionality(it) }

        return actualKeys.filter { it.lowercase() !in patternKeys && it !in ASSERT_KEYS }.map { UnexpectedKeyError(it) }
    }
}
