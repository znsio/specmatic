package io.specmatic.test.asserts

import io.ktor.http.*
import io.specmatic.core.Result
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.Value

enum class ExistenceCheckType(val value: String, val errorMessageSuffix: String) {
    EXISTS("exists", "exist"),
    NOT_EXISTS("not_exists", "not exist"),
    IS_NULL("is_null", "be ${NullValue.displayableValue()}"),
    NOT_NULL("is_not_null", "not be ${NullValue.displayableValue()}"),;

    companion object {
        fun fromString(value: String): ExistenceCheckType? {
            return entries.find { it.value == value }
        }
    }
}

class AssertExistence(override val prefix: String, override val key: String, val checkType: ExistenceCheckType): Assert {
    override fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val prefixValue = currentFactStore[prefix] ?: return Result.Failure(breadCrumb = prefix, message = "Could not resolve ${prefix.quote()} in current fact store")

        val dynamicList = dynamicAsserts(prefixValue)
        val results = dynamicList.map { newAssert ->
            val finalKey = newAssert.combinedKey
            val actualValue = currentFactStore[finalKey]
            assert(finalKey, actualValue)
        }

        return results.toResult()
    }

    override fun dynamicAsserts(prefixValue: Value): List<Assert> {
        return prefixValue.suffixIfMoreThanOne {suffix, _ ->
            AssertExistence(prefix = "$prefix$suffix", key = key, checkType = checkType)
        }
    }

    private fun assert(finalKey: String, actualValue: Value?): Result {
        val success = when (checkType) {
            ExistenceCheckType.EXISTS -> actualValue != null
            ExistenceCheckType.NOT_EXISTS -> actualValue == null
            ExistenceCheckType.NOT_NULL -> actualValue !is NullValue
            ExistenceCheckType.IS_NULL -> actualValue is NullValue
        }

        return if (success) { Result.Success() } else Result.Failure(
            breadCrumb = finalKey,
            message = "Expected ${finalKey.quote()} to ${checkType.errorMessageSuffix}"
        )
    }

    companion object {
        fun parse(prefix: String, key: String, value: Value): AssertExistence? {
            val match = ASSERT_PATTERN.find(value.toStringLiteral()) ?: return null
            val keyPrefix = prefix.removeSuffix(".${key}")

            val assertType = ExistenceCheckType.fromString(match.groupValues[1]) ?: return null
            return AssertExistence(keyPrefix, key, assertType)
        }
    }
}