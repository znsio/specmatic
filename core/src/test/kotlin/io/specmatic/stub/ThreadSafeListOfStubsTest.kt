package io.specmatic.stub

import io.mockk.every
import io.mockk.mockk
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

class ThreadSafeListOfStubsTest {

    @Nested
    inner class StubAssociatedToTests {

        @Test
        fun `should return a ThreadSafeListOfStubs for a given port`() {
            val specToPortMap = mapOf(
                "spec1.yaml" to 8080
            )
            val httpStubs = mutableListOf(
                mockk<HttpStubData> {
                    every { contractPath } returns "spec1.yaml"
                },
                mockk<HttpStubData> {
                    every { contractPath } returns "spec2.yaml"
                }
            )

            val threadSafeList = ThreadSafeListOfStubs(httpStubs, specToPortMap)

            val result = threadSafeList.stubAssociatedTo(port = 8080, defaultPort = 9090)

            assertNotNull(result)
            assertThat(result?.size).isEqualTo(1)
        }

        @Test
        fun `should return null if port has no associated stubs`() {
            val specToPortMap = mapOf(
                "spec1.yaml" to 8080,
                "spec2.yaml" to 8080,
                "spec3.yaml" to 8000
            )
            val httpStubs = mutableListOf(
                mockk<HttpStubData> {
                    every { contractPath } returns "spec1.yaml"
                },
                mockk<HttpStubData> {
                    every { contractPath } returns "spec2.yaml"
                }
            )

            val threadSafeList = ThreadSafeListOfStubs(httpStubs, specToPortMap)

            val result = threadSafeList.stubAssociatedTo(port = 8000, defaultPort = 9090)

            assertThat(result.size).isEqualTo(0)
        }

        @Test
        fun `should return a ThreadSafeListOfStubs for the default port if port not found in map`() {
            val specToPortMap = mapOf(
                "spec1.yaml" to 8080
            )
            val httpStubs = mutableListOf(
                mockk<HttpStubData> {
                    every { contractPath } returns "spec1.yaml"
                },
                mockk<HttpStubData> {
                    every { contractPath } returns "spec2.yaml"
                },
                mockk<HttpStubData> {
                    every { contractPath } returns "spec3.yaml"
                }
            )

            val threadSafeList = ThreadSafeListOfStubs(httpStubs, specToPortMap)

            val result = threadSafeList.stubAssociatedTo(port = 9090, defaultPort = 9090)

            assertNotNull(result)
            assertEquals(2, result!!.size)
        }

        @Test
        fun `should return multiple stubs associated with the same port`() {
            val specToPortMap = mapOf(
                "spec1.yaml" to 8080,
                "spec2.yaml" to 8080,
                "spec3.yaml" to 8080
            )
            val httpStubs = mutableListOf(
                mockk<HttpStubData> {
                    every { contractPath } returns "spec1.yaml"
                },
                mockk<HttpStubData> {
                    every { contractPath } returns "spec2.yaml"
                },
                mockk<HttpStubData> {
                    every { contractPath } returns "spec3.yaml"
                }
            )

            val threadSafeList = ThreadSafeListOfStubs(httpStubs, specToPortMap)

            val result = threadSafeList.stubAssociatedTo(port = 8080, defaultPort = 9090)

            assertNotNull(result)
            assertEquals(3, result!!.size)
        }

        @Test
        fun `should return an empty list if no stubs exist`() {
            val specToPortMap = mapOf("spec1.yaml" to 8080)
            val httpStubs = mutableListOf<HttpStubData>()

            val threadSafeList = ThreadSafeListOfStubs(httpStubs, specToPortMap)

            val result = threadSafeList.stubAssociatedTo(port = 8080, defaultPort = 9090)

            assertThat(result.size).isEqualTo(0)
        }
    }
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
        val expectations = ThreadSafeListOfStubs(sandwichedSpecificExpectation, emptyMap())

        @Test
        fun `it should prioritize specific over general expectations`() {
            val responseToSpecificValue = expectations.matchingStaticStub(specificRequest)
            val expectedResponse = responseToSpecificValue.first ?: fail("Expected a response for the given request to be found")

            val jsonResponse = expectedResponse.response.body as JSONObjectValue
            assertThat(jsonResponse.findFirstChildByName("id")?.toStringLiteral()).isEqualTo("10")
        }

        @Test
        fun `it should use the general expectation when the specific does not match the request`() {
            val responseToSpecificValue = expectations.matchingStaticStub(generalRequest)
            val expectedResponse = responseToSpecificValue.first ?: fail("Expected a response for the given request to be found")

            val jsonResponse = expectedResponse.response.body as JSONObjectValue
            assertThat(jsonResponse.findFirstChildByName("id")?.toStringLiteral()).isEqualTo("20")
        }
    }

}