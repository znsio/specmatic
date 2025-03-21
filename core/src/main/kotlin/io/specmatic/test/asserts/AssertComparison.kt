package io.specmatic.test.asserts

import io.ktor.http.*
import io.specmatic.core.Result
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

val ASSERT_PATTERN = Regex("^\\$(\\w+)\\((.*)\\)$")

class AssertComparison(override val prefix: String, override val key: String, val lookupKey: String, val isEqualityCheck: Boolean): Assert {

    override fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val prefixValue = currentFactStore[prefix] ?: return Result.Failure(breadCrumb = prefix, message = "Could not resolve ${prefix.quote()} in current fact store")
        val expectedValue = actualFactStore[lookupKey] ?: return Result.Failure(breadCrumb = lookupKey, message = "Could not resolve ${lookupKey.quote()} in actual fact store")

        val dynamicList = dynamicAsserts(prefixValue)
        val results = dynamicList.map { newAssert ->
            val finalKey = newAssert.combinedKey
            val actualValue = currentFactStore[finalKey] ?: return@map Result.Failure(breadCrumb = finalKey, message = "Could not resolve ${finalKey.quote()} in current fact store")
            assert(finalKey, actualValue, expectedValue)
        }

        return results.toResult()
    }

    override fun dynamicAsserts(prefixValue: Value): List<Assert> {
        return prefixValue.suffixIfMoreThanOne {suffix, _ ->
            AssertComparison(prefix = "$prefix$suffix", key = key, lookupKey = lookupKey, isEqualityCheck = isEqualityCheck)
        }
    }

    private fun assert(finalKey: String, actualValue: Value, expectedValue: Value): Result {
        val match = actualValue.toStringLiteral() == expectedValue.toStringLiteral()
        val success = match == isEqualityCheck
        return if (success) { Result.Success() } else {
            val message = if (isEqualityCheck) "equal" else "not equal"
            Result.Failure(
                breadCrumb = finalKey,
                message = "Expected ${actualValue.displayableValue()} to $message ${expectedValue.displayableValue()}"
            )
        }
    }

    companion object {
        fun parse(prefix: String, key: String, value: Value): AssertComparison? {
            if (value !is StringValue) return null

            val match = ASSERT_PATTERN.find(value.nativeValue) ?: return null
            val keyPrefix = prefix.removeSuffix(".${key}")

            return when (match.groupValues[1]) {
                "eq" -> AssertComparison(keyPrefix, key, match.groupValues[2], isEqualityCheck = true)
                "neq" -> AssertComparison(keyPrefix, key, match.groupValues[2], isEqualityCheck = false)
                else -> null
            }
        }
    }
}