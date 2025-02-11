package io.specmatic.stub

import io.mockk.every
import io.mockk.mockk
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
}