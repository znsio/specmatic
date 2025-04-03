package io.specmatic.stub

import io.mockk.every
import io.mockk.mockk
import io.specmatic.core.HttpRequestPattern
import io.specmatic.core.HttpResponse
import io.specmatic.core.HttpResponsePattern
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.mock.ScenarioStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ThreadSafeListOfStubsTest {

    companion object {
        @JvmStatic
        fun defaultStubOrdering(): Stream<Arguments> {
            val categories = StubCategory.entries
            val types = StubType.entries
            return Stream.of(Arguments.of(
                categories.flatMap { cat -> types.map { type -> cat to type } }
            ))
        }
    }

    @Nested
    inner class StubAssociatedToTests {

        private fun mockHttpStub(
            contractPath: String, stubToken: String? = null,
            partial: ScenarioStub? = null, data: JSONObjectValue? = null
        ): HttpStubData {
            return mockk {
                every { this@mockk.contractPath } returns contractPath
                every { this@mockk.stubToken } returns stubToken
                every { this@mockk.partial } returns partial
                every { this@mockk.data } returns (data ?: JSONObjectValue())
            }
        }

        @Test
        fun `should return a ThreadSafeListOfStubs for a given port`() {
            val specToPortMap = mapOf(
                "spec1.yaml" to 8080
            )
            val httpStubs = mutableListOf(
                mockHttpStub("spec1.yaml"),
                mockHttpStub("spec2.yaml")
            )

            val threadSafeList = ThreadSafeListOfStubs(httpStubs, specToPortMap)

            val result = threadSafeList.stubAssociatedTo(port = 8080, defaultPort = 9090)

            assertNotNull(result)
            assertThat(result.size).isEqualTo(1)
        }

        @Test
        fun `should return null if port has no associated stubs`() {
            val specToPortMap = mapOf(
                "spec1.yaml" to 8080,
                "spec2.yaml" to 8080,
                "spec3.yaml" to 8000
            )
            val httpStubs = mutableListOf(
                mockHttpStub("spec1.yaml"),
                mockHttpStub("spec2.yaml")
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
                mockHttpStub("spec1.yaml"),
                mockHttpStub("spec2.yaml"),
                mockHttpStub("spec3.yaml")
            )

            val threadSafeList = ThreadSafeListOfStubs(httpStubs, specToPortMap)

            val result = threadSafeList.stubAssociatedTo(port = 9090, defaultPort = 9090)

            assertNotNull(result)
            assertEquals(2, result.size)
        }

        @Test
        fun `should return multiple stubs associated with the same port`() {
            val specToPortMap = mapOf(
                "spec1.yaml" to 8080,
                "spec2.yaml" to 8080,
                "spec3.yaml" to 8080
            )
            val httpStubs = mutableListOf(
                mockHttpStub("spec1.yaml"),
                mockHttpStub("spec2.yaml"),
                mockHttpStub("spec3.yaml")
            )

            val threadSafeList = ThreadSafeListOfStubs(httpStubs, specToPortMap)

            val result = threadSafeList.stubAssociatedTo(port = 8080, defaultPort = 9090)

            assertNotNull(result)
            assertEquals(3, result.size)
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
    inner class StubOrderingTests {

        private fun stubOf(category: StubCategory, type: StubType): HttpStubData {
            return HttpStubData(
                stubToken = if (category == StubCategory.TRANSIENT) "token123" else null,
                partial = if (type == StubType.PARTIAL) ScenarioStub() else null,
                data = if (type == StubType.DATA_LOOKUP) JSONObjectValue(mapOf("key" to StringValue("value"))) else JSONObjectValue(),
                requestType = HttpRequestPattern(), resolver = Resolver(),
                response = HttpResponse.OK, responsePattern = HttpResponsePattern()
            )
        }

        private fun assertStubOrder(list: ThreadSafeListOfStubs, expectedOrder: List<Pair<StubCategory, StubType>>) {
            list.matchResults { stubs ->
                assertThat(stubs.map { ClassifiedStub.from(it).category to ClassifiedStub.from(it).type }).containsExactlyElementsOf(expectedOrder)
                emptyList()
            }
        }

        @ParameterizedTest
        @MethodSource("io.specmatic.stub.ThreadSafeListOfStubsTest#defaultStubOrdering")
        fun `stubData should be able to sort itself into the expected order`(expectedOrder: List<Pair<StubCategory, StubType>>) {
            val stubs = expectedOrder.map { (category, type) -> ClassifiedStub.from(stubOf(category, type)) }.shuffled()
            assertThat(stubs.sorted().map { it.category to it.type }).isEqualTo(expectedOrder)
        }

        @ParameterizedTest
        @MethodSource("io.specmatic.stub.ThreadSafeListOfStubsTest#defaultStubOrdering")
        fun `should sort the stubs based on category and type on initialization`(expectedOrder: List<Pair<StubCategory, StubType>>) {
            val threadSafeListOfStubs = ThreadSafeListOfStubs(
                httpStubs = expectedOrder.map { (category, type) -> stubOf(category, type) }.shuffled(),
                specToPortMap = emptyMap()
            )
            assertStubOrder(threadSafeListOfStubs, expectedOrder)
        }

        @Test
        fun `incremental addition of stubs should maintain the ordering`() {
            val list = ThreadSafeListOfStubs(emptyList(), emptyMap())
            val orderedPairs = listOf(
                StubCategory.TRANSIENT to StubType.NORMAL,
                StubCategory.PERSISTENT to StubType.NORMAL,
                StubCategory.PERSISTENT to StubType.PARTIAL
            )

            orderedPairs.shuffled().forEach { (category, type) ->
                val stub = stubOf(category, type)
                list.addToStub(
                    Pair(Result.Success(), stub),
                    ScenarioStub(stubToken = stub.stubToken, delayInMilliseconds = 0)
                )
            }

            assertStubOrder(list, orderedPairs)
        }

        @Test
        fun `should maintain order post removal of stubs`() {
            val orderedPairs = listOf(
                StubCategory.PERSISTENT to StubType.NORMAL,
                StubCategory.PERSISTENT to StubType.PARTIAL,
                StubCategory.TRANSIENT to StubType.NORMAL,
                StubCategory.TRANSIENT to StubType.PARTIAL
            )

            val list = ThreadSafeListOfStubs(orderedPairs.map { (category, type) -> stubOf(category, type) }, emptyMap())
            list.remove(stubOf(StubCategory.PERSISTENT, StubType.NORMAL))
            list.remove(stubOf(StubCategory.TRANSIENT, StubType.NORMAL))

            assertStubOrder(list, listOf(
                StubCategory.TRANSIENT to StubType.PARTIAL,
                StubCategory.PERSISTENT to StubType.PARTIAL
            ))
        }

        @Test
        fun `should maintain order post removal of stubs based on token`() {
            val list = ThreadSafeListOfStubs(
                listOf(
                    StubCategory.TRANSIENT to StubType.NORMAL,
                    StubCategory.TRANSIENT to StubType.PARTIAL,
                    StubCategory.PERSISTENT to StubType.NORMAL,
                    StubCategory.PERSISTENT to StubType.PARTIAL
                ).map { (category, type) -> stubOf(category, type) },
                emptyMap()
            )
            list.removeWithToken("token123")

            assertStubOrder(list, listOf(
                StubCategory.PERSISTENT to StubType.NORMAL,
                StubCategory.PERSISTENT to StubType.PARTIAL
            ))
        }

        @Test
        fun `partial data lookup example should be classified as data lookup example and not partial`() {
            val httpStubData = stubOf(StubCategory.PERSISTENT, StubType.PARTIAL).copy(data = JSONObjectValue(mapOf("key" to StringValue("value"))))
            val classifiedStub = ClassifiedStub.from(httpStubData)

            assertThat(classifiedStub.category).isEqualTo(StubCategory.PERSISTENT)
            assertThat(classifiedStub.type).isEqualTo(StubType.DATA_LOOKUP)
        }
    }
}