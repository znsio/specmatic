package io.specmatic.test

import io.specmatic.core.*
import io.specmatic.core.pattern.Row
import io.specmatic.core.value.*
import io.specmatic.test.asserts.Assert
import io.specmatic.test.asserts.parsedAssert

object ExamplePostValidator: ResponseValidator {

    override fun postValidate(scenario: Scenario, httpRequest: HttpRequest, httpResponse: HttpResponse): Result? {
        if (scenario.isNegative) return null
        val asserts  = scenario.exampleRow?.toAsserts(scenario)?.takeIf { it.isNotEmpty() } ?: return null

        val actualFactStore = httpRequest.toFactStore(scenario) + ExampleProcessor.getFactStore()
        val currentFactStore = httpResponse.toFactStore()

        val results = asserts.map { it.assert(currentFactStore, actualFactStore) }

        val finalResults = results.filterIsInstance<Result.Failure>().ifEmpty { return null }
        return Result.fromFailures(finalResults)
    }

    private fun Row.toAsserts(scenario: Scenario): List<Assert> {
        val responseExampleBody = this.responseExampleForAssertion ?: return emptyList()

        val headerAsserts = responseExampleBody.headers.map {
            parsedAssert("RESPONSE.HEADERS", it.key, StringValue(it.value), scenario.resolver)
        }.filterNotNull()

        return responseExampleBody.body.traverse(
            onScalar = { value, key -> mapOf(key to parsedAssert("RESPONSE.BODY", key, value, scenario.resolver)) },
            onAssert = { value, key -> mapOf(key to parsedAssert("RESPONSE.BODY", key, value, scenario.resolver)) }
        ).values.filterNotNull() + headerAsserts
    }

    private fun HttpRequest.toFactStore(scenario: Scenario): Map<String, Value> {
        val pathParams = if(scenario.httpRequestPattern.httpPathPattern != null && this.path != null) {
            scenario.httpRequestPattern.httpPathPattern.extractPathParams(this.path, scenario.resolver).toFactStore("REQUEST.PATH-PARAMS")
        } else emptyMap()

        val queryParams = queryParams.asMap().toFactStore("REQUEST.QUERY-PARAMS")
        val headers = headers.toFactStore("REQUEST.HEADERS")

        return body.traverse(
            prefix = "REQUEST.BODY", onScalar = ::toFactEntry, onComposite = ::toFactEntry
        ) + pathParams + queryParams + headers + mapOf("REQUEST" to this.toJSON())
    }

    private fun HttpResponse.toFactStore(): Map<String, Value> {
        val headers = headers.toFactStore("RESPONSE.HEADERS")

        return body.traverse(
            prefix = "RESPONSE.BODY", onScalar = ::toFactEntry, onComposite = ::toFactEntry
        ) + headers + mapOf("RESPONSE" to this.toJSON())
    }

    private fun Map<String, String>.toFactStore(prefix: String) = mapKeys { "$prefix.${it.key}" }.mapValues { StringValue(it.value) }

    private fun toFactEntry(value: Value, prefix: String): Map<String, Value> = mapOf(prefix to value)
}
