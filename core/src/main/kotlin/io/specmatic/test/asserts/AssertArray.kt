package io.specmatic.test.asserts

import io.ktor.http.*
import io.specmatic.core.Result
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

enum class ArrayAssertType { ARRAY_HAS }

class AssertArray(override val prefix: String, override val key: String, val lookupKey: String, val arrayAssertType: ArrayAssertType): Assert {
    override fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val prefixValue = currentFactStore[prefix] ?: return Result.Failure(breadCrumb = prefix, message = "Could not resolve ${prefix.quote()} in current fact store")
        if (prefixValue !is JSONArrayValue) {
            return Result.Failure(breadCrumb = prefix, message = "Expected ${prefix.quote()} to be an array")
        }

        return when (arrayAssertType) {
            ArrayAssertType.ARRAY_HAS -> assertArrayHas(prefixValue, currentFactStore, actualFactStore)
        }
    }

    private fun assertArrayHas(prefixValue: JSONArrayValue, currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val expectedValue = actualFactStore[lookupKey] ?: return Result.Failure(breadCrumb = lookupKey, message = "Could not resolve ${lookupKey.quote()} in actual fact store")
        val asserts = AssertComparison(prefix = prefix, key = key, lookupKey = lookupKey, isEqualityCheck = true).dynamicAsserts(prefixValue)
        val result = asserts.map { it.assert(currentFactStore, actualFactStore) }.toResultIfAny()

        return when (result) {
            is Result.Success -> Result.Success()
            is Result.Failure -> Result.Failure("None of the values in \"$prefix[*].$key\" matched ${lookupKey.quote()} of value ${expectedValue.displayableValue()}", breadCrumb = prefix)
        }
    }

    override fun dynamicAsserts(prefixValue: Value): List<AssertArray> { return listOf(this) }

    companion object {
        fun parse(prefix: String, key: String, value: Value): AssertArray? {
            if (value !is StringValue) return null

            val match = ASSERT_PATTERN.find(value.nativeValue) ?: return null
            val keyPrefix = prefix.removeSuffix(".${key}")

            return when (match.groupValues[1]) {
                "array_has" -> AssertArray(keyPrefix, key, match.groupValues[2], ArrayAssertType.ARRAY_HAS)
                else -> null
            }
        }
    }
}