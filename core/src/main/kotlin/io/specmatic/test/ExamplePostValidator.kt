package io.specmatic.test

import io.specmatic.core.*
import io.specmatic.core.pattern.Row
import io.specmatic.core.value.*
import io.specmatic.test.asserts.Assert
import io.specmatic.test.asserts.isKeyAssert
import io.specmatic.test.asserts.parsedAssert

object ExamplePostValidator: ResponseValidator {

    override fun validate(scenario: Scenario, httpRequest: HttpRequest, httpResponse: HttpResponse): Result? {
        val asserts  = scenario.exampleRow?.toAsserts()?.takeIf { it.isNotEmpty() } ?: return null

        val actualFactStore = httpRequest.toFactStore() + ExampleProcessor.getFactStore()
        val currentFactStore = httpResponse.toFactStore()

        val results = asserts.map { it.assert(currentFactStore, actualFactStore) }

        val finalResults = results.filterIsInstance<Result.Failure>().ifEmpty { return Result.Success() }
        return Result.fromFailures(finalResults)
    }

    private fun Row.toAsserts(): List<Assert> {
        val responseExampleBody = this.responseExampleForValidation?.responseExample?.body ?: return emptyList()

        return responseExampleBody.traverse(
            onScalar = { value, key -> mapOf(key to parsedAssert("RESPONSE.BODY", key, value)) },
            onAssert = { value, key -> mapOf(key to parsedAssert("RESPONSE.BODY", key, value)) }
        ).values.filterNotNull()
    }

    private fun HttpRequest.toFactStore(): Map<String, Value> {
        val queryParams = this.queryParams.asMap().map {
            "REQUEST.QUERY-PARAMS.${it.key}" to StringValue(it.value)
        }.toMap()

        val headers = this.headers.map {
            "REQUEST.HEADERS.${it.key}" to StringValue(it.value)
        }.toMap()

        return this.body.traverse(
            prefix = "REQUEST.BODY",
            onScalar = { value, key -> mapOf(key to value) },
            onComposite = { value, key -> mapOf(key to value) }
        ) + queryParams + headers
    }

    private fun HttpResponse.toFactStore(): Map<String, Value> {
        val headers = this.headers.map {
            "RESPONSE.HEADERS.${it.key}" to StringValue(it.value)
        }.toMap()

        return this.body.traverse(
            prefix = "RESPONSE.BODY",
            onScalar = { value, key -> mapOf(key to value) },
            onComposite = { value, key -> mapOf(key to value) }
        ) + headers
    }
}

internal fun <T> Value.traverse(
    prefix: String = "", onScalar: (Value, String) -> Map<String, T>,
    onComposite: ((Value, String) -> Map<String, T>)? = null, onAssert: ((Value, String) ->  Map<String, T>)? = null
): Map<String, T> {
    return when (this) {
        is JSONObjectValue -> this.traverse(prefix, onScalar, onComposite, onAssert)
        is JSONArrayValue ->  this.traverse(prefix, onScalar, onComposite, onAssert)
        is ScalarValue -> onScalar(this, prefix)
        else -> emptyMap()
    }.filterValues { it != null }
}

private fun <T> JSONObjectValue.traverse(
    prefix: String = "", onScalar: (Value, String) -> Map<String, T>,
    onComposite: ((Value, String) -> Map<String, T>)? = null, onAssert: ((Value, String) ->  Map<String, T>)? = null
): Map<String, T> {
    return this.jsonObject.entries.flatMap { (key, value) ->
        val fullKey = if (prefix.isNotEmpty()) "$prefix.$key" else key
        key.isKeyAssert {
            onAssert?.invoke(value, fullKey)?.entries.orEmpty()
        } ?: value.traverse(fullKey, onScalar, onComposite, onAssert).entries
    }.associate { it.toPair() } + onComposite?.invoke(this, prefix).orEmpty()
}

private fun <T> JSONArrayValue.traverse(
    prefix: String = "", onScalar: (Value, String) -> Map<String, T>,
    onComposite: ((Value, String) -> Map<String, T>)? = null, onAssert: ((Value, String) ->  Map<String, T>)? = null
): Map<String, T> {
    return this.list.mapIndexed { index, value ->
        val fullKey = if (onAssert != null) { prefix } else "$prefix[$index]"
        value.traverse(fullKey, onScalar, onComposite, onAssert)
    }.flatMap { it.entries }.associate { it.toPair() } + onComposite?.invoke(this, prefix).orEmpty()
}
