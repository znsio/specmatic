package io.specmatic.test.asserts

import io.specmatic.core.Result
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.ScalarValue
import io.specmatic.core.value.Value
import kotlin.math.exp

val ASSERT_PATTERN = Regex("^\\$(\\w+)\\((.*)\\)$")

class AssertComparison(val prefix: String, val key: String, val lookupKey: String, private val isEqualityCheck: Boolean): Assert {

    override fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val prefixValue = currentFactStore[prefix] ?: return Result.Failure(breadCrumb = prefix, message = "Could not resolve $prefix in current fact store")
        val actualValue = currentFactStore["$prefix.$key"] ?: return Result.Failure(breadCrumb = lookupKey, message = "Could not resolve $lookupKey in actual current fact store")
        val expectedValue = actualFactStore[lookupKey] ?: return Result.Failure(breadCrumb = lookupKey, message = "Could not resolve $lookupKey in expected actual fact store")

        return assert(prefixValue, actualValue, expectedValue)
    }

    private fun assert(value: Value, actualValue: Value, expectedValue: Value): Result {
        return when(value) {
            is JSONObjectValue -> assert(value, actualValue, expectedValue)
            is JSONArrayValue -> assert(value, actualValue, expectedValue)
            else -> Result.Failure(breadCrumb = key, message = "Expected value to be a scalar, array or object")
        }
    }

    private fun assert(value: JSONObjectValue, actualValue: Value, expectedValue: Value): Result {
        val matches = actualValue.toStringLiteral() == expectedValue.toStringLiteral()
        return when (isEqualityCheck) {
            true -> if (matches) Result.Success() else Result.Failure(breadCrumb = key, message = "Expected $actualValue to equal $expectedValue")
            false -> if (!matches) Result.Success() else Result.Failure(breadCrumb = key, message = "Expected $actualValue to not equal $expectedValue")
        }
    }

    private fun assert(value: JSONArrayValue, actualValue: Value, expectedValue: Value): Result {
        val results = value.list.map { assert(value, actualValue, expectedValue) }
        return Result.fromResults(results)
    }

    companion object {
        fun parse(prefix: String, key: String, value: Value): AssertComparison? {
            val match = ASSERT_PATTERN.find(value.toStringLiteral()) ?: return null
            val keyPrefix = prefix.removeSuffix(".${key}")

            return when (match.groupValues[1]) {
                "eq" -> AssertComparison(keyPrefix, key, match.groupValues[2], isEqualityCheck = true)
                "neq" -> AssertComparison(keyPrefix, key, match.groupValues[2], isEqualityCheck = false)
                else -> null
            }
        }
    }
}