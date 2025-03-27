package io.specmatic.test.asserts

import io.ktor.http.*
import io.specmatic.core.Result
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

enum class ArrayAssertType { ARRAY_HAS }

class AssertArray(override val keys: List<String>, val lookupKey: String, val arrayAssertType: ArrayAssertType): Assert {

    init {
        require(keys[keys.lastIndex - 1].matches("\\[\\*]|\\[\\d+]".toRegex())) {
            throw ContractException(
                breadCrumb = combinedKey,
                errorMessage = "Array Asserts can only be used on arrays"
            )
        }
    }

    override fun execute(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        return AssertComparison(keys = keys, lookupKey = lookupKey, isEqualityCheck = true).execute(currentFactStore, actualFactStore)
    }

    private fun List<String>.wildCardIndex(): String {
        return this.reduce { acc, key ->
            if (key.startsWith("[")) "$acc[*]" else "$acc.$key"
        }
    }

    override fun List<Result>.toResult(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val expectedValue = actualFactStore[lookupKey] ?: return Result.Failure(
            breadCrumb = lookupKey,
            message = "Could not resolve ${lookupKey.quote()} in store"
        )

        val indexedKeys = generateDynamicPaths(keys, currentFactStore) { NullValue }.firstOrNull() ?: keys
        return this.firstOrNull { it is Result.Success } ?: Result.Failure(
            breadCrumb = indexedKeys.wildCardIndex(),
            message = "None of the values matched ${lookupKey.quote()} of value ${expectedValue.displayableValue()}",
        )
    }

    override fun dynamicAsserts(currentFactStore: Map<String, Value>, ifNotExists: (String) -> Value): List<AssertArray> {
        return this.generateDynamicPaths(keys, currentFactStore, ifNotExists = ifNotExists).map { keys ->
            AssertArray(keys, lookupKey, arrayAssertType)
        }
    }

    companion object {
        fun parse(keys: List<String>, value: Value): AssertArray? {
            if (value !is StringValue) return null
            val match = ASSERT_PATTERN.find(value.nativeValue) ?: return null
            return when (match.groupValues[1]) {
                "array_has" -> AssertArray(keys, match.groupValues[2], ArrayAssertType.ARRAY_HAS)
                else -> null
            }
        }
    }
}