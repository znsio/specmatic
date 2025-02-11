package io.specmatic.stub

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class ThreadSafeListOfStubsTest {

    @Nested
    inner class StubAssociatedToTests {

        private fun String.canonicalPath(): String {
            return File(this).canonicalPath
        }

        @Test
        fun `should return a ThreadSafeListOfStubs for a given port`() {
            val specToPortMap = mapOf(
                "spec1.yaml".canonicalPath() to 8080
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

            val result = threadSafeList.stubAssociatedTo(defaultPort = 9090, port = 8080)

            assertNotNull(result)
            assertThat(result?.size).isEqualTo(1)
        }

        @Test
        fun `should return null if port has no associated stubs`() {
            val specToPortMap = mapOf(
                "spec1.yaml".canonicalPath() to 8080,
                "spec2.yaml".canonicalPath() to 8080,
                "spec3.yaml".canonicalPath() to 8000
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

            val result = threadSafeList.stubAssociatedTo(defaultPort = 9090, port = 8000)

            assertNull(result)
        }

        @Test
        fun `should return a ThreadSafeListOfStubs for the default port if port not found in map`() {
            val specToPortMap = mapOf(
                "spec1.yaml".canonicalPath() to 8080
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

            val result = threadSafeList.stubAssociatedTo(defaultPort = 9090, port = 9090)

            assertNotNull(result)
            assertEquals(2, result!!.size)
        }

        @Test
        fun `should return multiple stubs associated with the same port`() {
            val specToPortMap = mapOf(
                "spec1.yaml".canonicalPath() to 8080,
                "spec2.yaml".canonicalPath() to 8080,
                "spec3.yaml".canonicalPath() to 8080
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

            val result = threadSafeList.stubAssociatedTo(defaultPort = 9090, port = 8080)

            assertNotNull(result)
            assertEquals(3, result!!.size)
        }

        @Test
        fun `should return null if no stubs exist`() {
            val specToPortMap = mapOf("spec1.yaml" to 8080)
            val httpStubs = mutableListOf<HttpStubData>()

            val threadSafeList = ThreadSafeListOfStubs(httpStubs, specToPortMap)

            val result = threadSafeList.stubAssociatedTo(defaultPort = 9090, port = 8080)

            assertNull(result)
        }
    }
}