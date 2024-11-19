package io.specmatic.test

import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.pattern.Row
import io.specmatic.core.value.*

enum class AssertType(val value: String) {
    ASSERT_EQUAL("eq"),
    ASSERT_NOT_EQUAL("neq");

    companion object {
        fun fromValue(value: String): AssertType {
            return entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Unknown assert type: $value")
        }
    }
}

val ASSERT_PATTERN = Regex("^\\$(\\w+)\\((.*)\\)$")

data class Assert(val type: AssertType, val lookupKey: String)

object ExamplePostValidator: ResponseValidator {
    private var factStore = mapOf<String, Value>()

    override fun validate(scenario: Scenario, httpRequest: HttpRequest, httpResponse: HttpResponse): Result? {
        val bodyAsserts = scenario.exampleRow?.toAsserts() ?: return null

        factStore = httpRequest.toFactStore() + ExampleProcessor.getFactStore()
        val responseStore = httpResponse.toFactStore()
        val results = bodyAsserts.map { (key, assert) ->
            val factValue = factStore[assert.lookupKey] ?: return@map Result.Failure(breadCrumb = key, message = "Could not resolve ${assert.lookupKey} in fact store")
            val actualValue = responseStore[key] ?: return@map Result.Failure(breadCrumb = key, message = "Could not resolve $key in response store")
            when (assert.type) {
                AssertType.ASSERT_EQUAL -> {
                    if (factValue.toStringLiteral() != actualValue.toStringLiteral()) {
                        Result.Failure(breadCrumb = key, message = "Expected $key to be equal to '$factValue`, but got '$actualValue'")
                    } else Result.Success()
                }
                AssertType.ASSERT_NOT_EQUAL -> {
                    if (factValue.toStringLiteral() == actualValue.toStringLiteral()) {
                        Result.Failure(breadCrumb = key, message = "Expected $key to not be equal to '$factValue', but got '$actualValue'")
                    } else Result.Success()
                }
            }
        }

        return Result.fromResults(results)
    }

    private fun HttpRequest.toFactStore(): Map<String, Value> {
        return this.body.traverse(
            prefix = "REQUEST.BODY",
            onScalar = { value, key -> mapOf(key to value) },
            onComposite = { value, key -> mapOf(key to value) }
        )
    }

    private fun HttpResponse.toFactStore(): Map<String, Value> {
        return this.body.traverse(
            onScalar = { value, key -> mapOf(key to value) },
            onComposite = { value, key -> mapOf(key to value) }
        )
    }

    private fun Row.toAsserts(): Map<String, Assert>? {
        return this.responseExampleForValidation?.responseExample?.body?.toAsserts()
    }

    private fun Value.toAsserts(): Map<String, Assert> {
        return this.traverse(
            onScalar = { value, key ->
                val matchResult = ASSERT_PATTERN.find(value.toStringLiteral()) ?: return@traverse emptyMap()
                mapOf(key to Assert(AssertType.fromValue(matchResult.groupValues[1]), matchResult.groupValues[2]))
            }
        )
    }
}

internal fun <T> Value.traverse(
    prefix: String = "",
    onScalar: (Value, String) -> Map<String, T>,
    onComposite: (Value, String) -> Map<String, T> = { _, _ -> emptyMap() }
): Map<String, T> {
    return when (this) {
        is JSONObjectValue -> this.traverse(prefix, onScalar, onComposite)
        is JSONArrayValue ->  this.traverse(prefix, onScalar, onComposite)
        is ScalarValue -> onScalar(this, prefix)
        else -> emptyMap()
    }
}

private fun <T> JSONObjectValue.traverse(
    prefix: String = "", onScalar: (Value, String) -> Map<String, T>, onComposite: (Value, String) -> Map<String, T>
): Map<String, T> {
    return this.jsonObject.entries.flatMap { (key, value) ->
        val fullKey = if (prefix.isNotEmpty()) "$prefix.$key" else key
        value.traverse(fullKey, onScalar, onComposite).entries
    }.associate { it.toPair() } + onComposite(this, prefix)
}

private fun <T> JSONArrayValue.traverse(
    prefix: String = "", onScalar: (Value, String) -> Map<String, T>, onComposite: (Value, String) -> Map<String, T>
): Map<String, T> {
    return this.list.mapIndexed { index, value ->
        val fullKey = "$prefix[$index]"
        value.traverse(fullKey, onScalar, onComposite)
    }.flatMap { it.entries }.associate { it.toPair() } + onComposite(this, prefix)
}
