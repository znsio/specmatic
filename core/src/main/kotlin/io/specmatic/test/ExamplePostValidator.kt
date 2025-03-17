package io.specmatic.test

import io.specmatic.core.*
import io.specmatic.core.pattern.Row
import io.specmatic.core.value.*
import io.specmatic.test.asserts.Assert
import io.specmatic.test.asserts.parsedAssert

object ExamplePostValidator: ResponseValidator {

    override fun postValidate(scenario: Scenario, httpRequest: HttpRequest, httpResponse: HttpResponse): Result? {
        if (scenario.isNegative) return null
        val asserts  = scenario.exampleRow?.toAsserts()?.takeIf { it.isNotEmpty() } ?: return null

        val actualFactStore = httpRequest.toFactStore() + ExampleProcessor.getFactStore()
        val currentFactStore = httpResponse.toFactStore()

        val results = asserts.map { it.assert(currentFactStore, actualFactStore) }

        val finalResults = results.filterIsInstance<Result.Failure>().ifEmpty { return null }
        return Result.fromFailures(finalResults)
    }

    private fun Row.toAsserts(): List<Assert> {
        val responseExampleBody = this.responseExampleForAssertion ?: return emptyList()

        val headerAsserts = responseExampleBody.headers.map {
            parsedAssert("RESPONSE.HEADERS", it.key, StringValue(it.value))
        }.filterNotNull()

        return responseExampleBody.body.traverse(
            onScalar = { value, key -> mapOf(key to parsedAssert("RESPONSE.BODY", key, value)) },
            onAssert = { value, key -> mapOf(key to parsedAssert("RESPONSE.BODY", key, value)) }
        ).values.filterNotNull() + headerAsserts
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
