package io.specmatic.test.asserts

import io.specmatic.core.Result
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.Value

interface Assert {
    fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result

    fun List<Result>.toResult(): Result {
        val failures = filterIsInstance<Result.Failure>()
        return if (failures.isNotEmpty()) {
            Result.fromFailures(failures)
        } else Result.Success()
    }
}

fun parsedAssert(prefix: String, key: String, value: Value): Assert? {
    return when (key) {
        "\$if" -> AssertConditional.parse(prefix, key, value)
        else -> {
            return AssertComparison.parse(prefix, key, value) ?: AssertArray.parse(prefix, key, value)
        }
    }
}

fun <T> Value.suffixIfMoreThanOne(block: (index: Int, suffix: String) -> T): List<T> {
    return when (this) {
        is JSONArrayValue -> (0 until this.list.size).mapNotNull { index -> block(index, "[$index]") }
        else -> listOfNotNull(block(0,""))
    }
}

fun <T> String.isKeyAssert(block: (String) -> T): T? {
    return if (this.startsWith("\$if") || this.startsWith("\$array")) {
        block(this)
    } else null
}
