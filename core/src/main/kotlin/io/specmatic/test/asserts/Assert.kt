package io.specmatic.test.asserts

import io.specmatic.core.Result
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.ScalarValue
import io.specmatic.core.value.Value

interface Assert {
    fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result

    fun dynamicAsserts(prefixValue: Value): List<Assert>

    fun List<Result>.toResult(): Result {
        val failures = filterIsInstance<Result.Failure>()
        return if (failures.isNotEmpty()) {
            Result.fromFailures(failures)
        } else Result.Success()
    }

    fun List<Result>.toResultIfAny(): Result {
        return this.firstOrNull { it is Result.Success } ?: this.toResult()
    }

    val prefix: String
    val key: String

    companion object {
        fun parse(prefix: String, key: String, value: Value): Assert? {
            return when (key) {
                "\$if" -> AssertConditional.parse(prefix, key, value)
                else -> if (value is ScalarValue) { parseScalarValue(prefix, key, value) } else null
            }
        }

        private fun parseScalarValue(prefix: String, key: String, value: Value): Assert? {
            return AssertComparison.parse(prefix, key, value) ?: AssertArray.parse(prefix, key, value) ?: AssertPattern.parse(prefix, key, value)
        }
    }
}

fun parsedAssert(prefix: String, key: String, value: Value): Assert? {
    return Assert.parse(prefix, key, value)
}

fun <T> Value.suffixIfMoreThanOne(block: (suffix: String, suffixValue: Value) -> T): List<T> {
    return when (this) {
        is JSONArrayValue -> this.list.mapIndexed { index, value -> block("[$index]", value) }
        else -> listOfNotNull(block("", this))
    }
}

fun <T> String.isKeyAssert(block: (String) -> T): T? {
    return if (this.startsWith("\$if")) {
        block(this)
    } else null
}
