package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.HttpResponsePattern
import io.specmatic.core.Resolver
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HttpExpectationsTest {
    @Nested
    inner class ExpectationPrioritization {
        private val specificRequest = HttpRequest("POST", "/products", body = parsedJSONObject("""{"name": "Specific Value"}"""))
        private val generalRequest = HttpRequest("POST", "/products", body = parsedJSONObject("""{"name": "(string)"}"""))

        private val specificExpectation = HttpStubData(
            requestType = specificRequest.toPattern(),
            response = HttpResponse.ok(parsedJSONObject("{\"id\": 10}")),
            responsePattern = HttpResponsePattern(HttpResponse.OK),
            resolver = Resolver(),
            originalRequest = specificRequest
        )

        private val generalExpectation = HttpStubData(
            requestType = generalRequest.toPattern(),
            response = HttpResponse.ok(parsedJSONObject("{\"id\": 20}")),
            responsePattern = HttpResponsePattern(HttpResponse.OK),
            resolver = Resolver(),
            originalRequest = generalRequest
        )

        private val sandwichedSpecificExpectation = mutableListOf(generalExpectation, specificExpectation, generalExpectation)
        val expectations = HttpExpectations(
            static = ThreadSafeListOfStubs(sandwichedSpecificExpectation, emptyMap())
        )

        @Test
        fun `it should prioritize specific over general expectations`() {
            val responseToSpecificValue = expectations.matchingStub(specificRequest)
            val expectedResponse = responseToSpecificValue.first ?: fail("Expected a response for the given request to be found")

            val jsonResponse = expectedResponse.response.body as JSONObjectValue
            assertThat(jsonResponse.findFirstChildByName("id")?.toStringLiteral()).isEqualTo("10")
        }

        @Test
        fun `it should use the general expectation when the specific does not match the request`() {
            val responseToSpecificValue = expectations.matchingStub(generalRequest)
            val expectedResponse = responseToSpecificValue.first ?: fail("Expected a response for the given request to be found")

            val jsonResponse = expectedResponse.response.body as JSONObjectValue
            assertThat(jsonResponse.findFirstChildByName("id")?.toStringLiteral()).isEqualTo("20")
        }
    }
}