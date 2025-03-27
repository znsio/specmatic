package io.specmatic.test.asserts

import io.ktor.http.*
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.ScalarValue
import io.specmatic.core.value.Value

val ASSERT_KEYS = setOf("\$if", "\$then", "\$else")

interface Assert {
    fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val dynamicAsserts = runCatching { dynamicAsserts(currentFactStore) }.getOrElse { e -> return e.toFailure() }
        return dynamicAsserts.map { it.execute(currentFactStore, actualFactStore) }.toResult(currentFactStore, actualFactStore)
    }

    fun dynamicAsserts(currentFactStore: Map<String, Value>, ifNotExists: (String) -> Value = ::throwIfNotExists): List<Assert>

    fun execute(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result

    fun generateDynamicPaths(
        remainingKeys: List<String>,
        store: Map<String, Value>,
        pathSoFar: List<String> = emptyList(),
        ifNotExists: (String) -> Value = ::throwIfNotExists
    ): List<List<String>> {
        if (remainingKeys.isEmpty()) return listOf(pathSoFar)

        val currentKey = remainingKeys.first()
        val newBase = (pathSoFar + currentKey).combinedKey()
        val currentValue = store[newBase] ?: ifNotExists(newBase)

        return currentValue.suffixIfMoreThanOne { suffix, _ ->
            val newPathSoFar = (pathSoFar + currentKey + suffix).filter(String::isNotEmpty)
            generateDynamicPaths(remainingKeys.drop(1), store, newPathSoFar, ifNotExists)
        }
    }

    fun List<Result>.toResult(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        return Result.fromResults(this)
    }

    val keys: List<String>
    val combinedKey: String
        get() = keys.combinedKey()

    fun List<String>.combinedKey(): String {
        return this.filter(String::isNotEmpty).reduce { acc, key -> if (key.startsWith("[")) acc + key else "$acc.$key" }
    }

    companion object {
        fun parse(keys: List<String>, value: Value, resolver: Resolver): Assert? {
            return when (keys.last()) {
                "\$if" -> AssertConditional.parse(keys, value, resolver)
                else -> if (value is ScalarValue) { parseScalarValue(keys, value, resolver) } else null
            }
        }

        private fun parseScalarValue(keys: List<String>, value: Value, resolver: Resolver): Assert? {
            return AssertComparison.parse(keys, value)
                ?: AssertArray.parse(keys, value)
                ?: AssertPattern.parse(keys, value, resolver)
                ?: AssertExistence.parse(keys, value)
        }

        private fun throwIfNotExists(key: String): Value {
            throw ContractException(
                breadCrumb = key,
                errorMessage = "Could not resolve ${key.quote()} in response"
            )
        }
    }
}

fun parsedAssert(prefix: String, key: String, value: Value, resolver: Resolver = Resolver()): Assert? {
    val prefixKeys = prefix.split('.').filter(String::isNotEmpty)
    val keyParts = key.split('.').filter(String::isNotEmpty)
    val keys = if (prefixKeys.takeLast(keyParts.size) == keyParts) prefixKeys else prefixKeys + keyParts
    return Assert.parse(keys, value, resolver)
}

fun <T> Value.suffixIfMoreThanOne(block: (suffix: String, suffixValue: Value) -> List<T>): List<T> {
    return when (this) {
        is JSONArrayValue -> this.list.flatMapIndexed { index, value -> block("[$index]", value) }
        else -> block("", this)
    }
}

fun <T> String.isKeyAssert(block: (String) -> T): T? {
    return if (this.startsWith("\$if")) {
        block(this)
    } else null
}

fun Throwable.toFailure(): Result.Failure {
    return when (this) {
        is ContractException -> this.failure()
        else -> Result.Failure(exceptionCauseMessage(this))
    }
}
