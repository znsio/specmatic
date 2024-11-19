package io.specmatic.test.asserts

import io.specmatic.core.Result
import io.specmatic.core.value.Value

val ASSERT_PATTERN = Regex("^\\$(\\w+)\\((.*)\\)$")

class AssertComparison(val prefix: String, val key: String, val lookupKey: String, private val isEqualityCheck: Boolean): Assert {

    override fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val prefixValue = currentFactStore[prefix] ?: return Result.Failure(breadCrumb = prefix, message = "Could not resolve $prefix in current fact store")
        val expectedValue = actualFactStore[lookupKey] ?: return Result.Failure(breadCrumb = lookupKey, message = "Could not resolve $lookupKey in actual fact store")

        val dynamicList = createDynamicList(prefixValue)
        val results = dynamicList.map { newAssert ->
            val finalKey = "${newAssert.prefix}.${newAssert.key}"
            val actualValue = currentFactStore[finalKey] ?: return@map Result.Failure(breadCrumb = finalKey, message = "Could not resolve $finalKey in current fact store")
            assert(actualValue, expectedValue)
        }

        return results.toResult()
    }

    private fun createDynamicList(prefixValue: Value): List<AssertComparison> {
        return prefixValue.suffixIfMoreThanOne {_, suffix ->
            AssertComparison(prefix = "$prefix$suffix", key = key, lookupKey = lookupKey, isEqualityCheck = isEqualityCheck)
        }
    }

    private fun assert(actualValue: Value, expectedValue: Value): Result {
        val match = actualValue.toStringLiteral() == expectedValue.toStringLiteral()
        return when (isEqualityCheck) {
            true -> if (match) Result.Success() else Result.Failure(breadCrumb = key, message = "Expected $actualValue to equal $expectedValue")
            false -> if (!match) Result.Success() else Result.Failure(breadCrumb = key, message = "Expected $actualValue to not equal $expectedValue")
        }
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