package io.specmatic.core.filters

import io.mockk.every
import io.mockk.mockk
import io.specmatic.core.filters.ScenarioMetadataFilter.Companion.filterUsing
import io.specmatic.test.ContractTest
import io.specmatic.test.ScenarioAsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ScenarioMetadataFilterTest {

    @Nested
    inner class IsSatisfiedByAllTests {
        @Test
        fun `should satisfy all filters when all criteria match`() {
            val filter = ScenarioMetadataFilter(
                methods = setOf("GET"),
                paths = setOf("/users"),
                statusCodes = setOf("200"),
                headers = setOf("Authorization"),
                queryParams = setOf("userId"),
                exampleNames = setOf("example1")
            )
            val metadata = ScenarioMetadata(
                method = "GET",
                path = "/users",
                statusCode = 200,
                header = setOf("Authorization"),
                query = setOf("userId"),
                exampleName = "example1"
            )
            assertThat(filter.isSatisfiedByAll(metadata)).isTrue()
        }

        @Test
        fun `should satisfy all filters with multiple headers and query params in metadata`() {
            val filter = ScenarioMetadataFilter(
                methods = setOf("GET"),
                paths = setOf("/users"),
                statusCodes = setOf("200"),
                headers = setOf("Authorization", "Content-Type"),
                queryParams = setOf("userId", "role"),
                exampleNames = setOf("example1")
            )
            val metadata = ScenarioMetadata(
                method = "GET",
                path = "/users",
                statusCode = 200,
                header = setOf("Authorization", "Content-Type", "Custom-Header"),
                query = setOf("userId", "role", "additionalQueryParam"),
                exampleName = "example1"
            )
            assertThat(filter.isSatisfiedByAll(metadata)).isTrue()
        }

        @Test
        fun `should satisfy all filters if all the filters are empty`() {
            val filter = ScenarioMetadataFilter()
            val metadata = ScenarioMetadata(
                method = "GET",
                path = "/any",
                statusCode = 200,
                header = emptySet(),
                query = emptySet(),
                exampleName = "example"
            )
            assertThat(filter.isSatisfiedByAll(metadata)).isTrue()
        }
    }

    @Nested
    inner class IsSatisfiedByAtLeastOneTests {
        @Test
        fun `should not satisfy at least one filter when no criteria match`() {
            val filter = ScenarioMetadataFilter(
                methods = setOf("POST"),
                paths = setOf("/products"),
                statusCodes = setOf("201"),
                headers = setOf("Authorization"),
                queryParams = setOf("userId"),
                exampleNames = setOf("example2")
            )
            val metadata = ScenarioMetadata(
                method = "GET",
                path = "/users",
                statusCode = 404,
                header = emptySet(),
                query = emptySet(),
                exampleName = "example3"
            )
            assertThat(filter.isSatisfiedByAtLeastOne(metadata)).isFalse()
        }

        @Test
        fun `should satisfy at least one filter with matching header`() {
            val filter = ScenarioMetadataFilter(
                headers = setOf("Authorization")
            )
            val metadata = ScenarioMetadata(
                method = "GET",
                path = "/any",
                statusCode = 200,
                header = setOf("Authorization", "X-Request-ID"),
                query = setOf("anyId"),
                exampleName = ""
            )
            assertThat(filter.isSatisfiedByAtLeastOne(metadata)).isTrue()
        }

        @Test
        fun `should satisfy at least one filter with matching query param`() {
            val filter = ScenarioMetadataFilter(
                queryParams = setOf("anyId", "productId")
            )
            val metadata = ScenarioMetadata(
                method = "GET",
                path = "/any",
                statusCode = 200,
                header = setOf("Authorization", "X-Request-ID"),
                query = setOf("anyId"),
                exampleName = "some-example"
            )
            assertThat(filter.isSatisfiedByAtLeastOne(metadata)).isTrue()
        }
    }

    @Nested
    inner class FilterStringToScenarioMetadataTests {
        @Test
        fun `should create the scenario metadata filter from the filter string`() {
            val filterString = "METHOD=POST,GET;STATUS=200;PATH=/users,/products"
            val filter = ScenarioMetadataFilter.from(filterString)

            assertThat(filter.methods).containsExactlyInAnyOrder("POST", "GET")
            assertThat(filter.statusCodes).containsExactly("200")
            assertThat(filter.paths).containsExactlyInAnyOrder("/users", "/products")
        }

        @Test
        fun `should create empty scenario metadata filter when filter string is empty`() {
            val filterString = ""
            val filter = ScenarioMetadataFilter.from(filterString)

            assertThat(filter.methods).isEmpty()
            assertThat(filter.statusCodes).isEmpty()
            assertThat(filter.paths).isEmpty()
            assertThat(filter.headers).isEmpty()
            assertThat(filter.queryParams).isEmpty()
            assertThat(filter.exampleNames).isEmpty()
        }

        @Test
        fun `should create the scenario metadata filter with multiple criteria for headers & query-params`() {
            val filterString = "METHOD=GET;HEADERS=Authorization,Content-Type;QUERY-PARAMS=productId,orderId;EXAMPLE-NAME=create-product"
            val filter = ScenarioMetadataFilter.from(filterString)

            assertThat(filter.methods).containsExactly("GET")
            assertThat(filter.headers).containsExactlyInAnyOrder("Authorization", "Content-Type")
            assertThat(filter.queryParams).containsExactlyInAnyOrder("productId", "orderId")
            assertThat(filter.exampleNames).containsExactlyInAnyOrder("create-product")
        }
    }

    @Nested
    inner class TestsForFilterUsingFunction {

        private fun getScenarioAsTestReturning(scenarioMetadata: ScenarioMetadata): ScenarioAsTest {
            return mockk<ScenarioAsTest> {
                every { scenario } returns mockk {
                    every { toScenarioMetadata() } returns scenarioMetadata
                }
            }
        }

        @Test
        fun `should return all tests if there is no filter involved`() {
            val tests = sequenceOf(
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        method = "POST",
                        path = "/product",
                        statusCode = 200,
                        header = emptySet(),
                        query = emptySet(),
                        exampleName = ""
                    )
                )
            )
            val filter = ScenarioMetadataFilter()
            val result = filterUsing(tests, filter, filter) { it.scenario.toScenarioMetadata() }

            assertEquals(tests.toList(), result.toList())
        }

        @Test
        fun `should return the test which satisfies the inclusion filter`() {
            val tests = sequenceOf(
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "GET",
                        "/path1",
                        200,
                        setOf("Content-Type: application/json"),
                        setOf("id=1"),
                        "example1"
                    )
                ),
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "POST",
                        "/path2",
                        404,
                        setOf("Content-Type: application/json"),
                        setOf("id=2"),
                        "example2"
                    )
                )
            )
            val includeFilter = ScenarioMetadataFilter(methods = setOf("GET"), paths = setOf("/path1"))
            val excludeFilter = ScenarioMetadataFilter()  // No exclusion

            val result = filterUsing(tests, includeFilter, excludeFilter) {
                it.scenario.toScenarioMetadata()
            }.toList()

            assertEquals(result.size, 1)
            assertThat(result.single()).isEqualTo(tests.first())
        }

        @Test
        fun `should return the test which satisfies the exclusion filter`() {
            val tests = sequenceOf(
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "GET",
                        "/path1",
                        200,
                        setOf("Content-Type: application/json"),
                        setOf("id=1"),
                        "example1"
                    )
                ),
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "POST",
                        "/path2",
                        404,
                        setOf("Content-Type: application/json"),
                        setOf("id=2"),
                        "example2"
                    )
                ),
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "GET",
                        "/path3",
                        500,
                        setOf("Content-Type: application/json"),
                        setOf("id=3"),
                        "example3"
                    )
                )
            )
            val includeFilter = ScenarioMetadataFilter()
            val excludeFilter = ScenarioMetadataFilter(statusCodes = setOf("200"))  // Exclude status code 200

            val result = filterUsing(tests, includeFilter, excludeFilter) {
                it.scenario.toScenarioMetadata()
            }.toList()

            assertEquals(
                tests.filterIndexed { index, _ -> index > 0 }.toList(),
                result.toList()
            )
        }

        @Test
        fun `should return the test which satisfies both the inclusion and exclusion filters`() {
            val tests = sequenceOf(
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "GET",
                        "/path1",
                        200,
                        setOf("Content-Type: application/json"),
                        setOf("id=1"),
                        "example1"
                    )
                ),
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "GET",
                        "/path1",
                        400,
                        setOf("Content-Type: application/json"),
                        setOf("id=1"),
                        "example1"
                    )
                ),
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "POST",
                        "/path2",
                        404,
                        setOf("Content-Type: application/json"),
                        setOf("id=2"),
                        "example2"
                    )
                )
            )
            val includeFilter = ScenarioMetadataFilter(methods = setOf("GET")) // Include GET methods
            val excludeFilter = ScenarioMetadataFilter(statusCodes = setOf("400"))  // Exclude 400 status codes

            val result = filterUsing(tests, includeFilter, excludeFilter) {
                it.scenario.toScenarioMetadata()
            }.toList()

            assertEquals(result.size, 1)
            assertThat(result.single()).isEqualTo(tests.first())
        }

        @Test
        fun `should return no test if the no test satisfies both inclusion and exclusion filters`() {
            val tests = sequenceOf(
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "GET",
                        "/path1",
                        200,
                        setOf(),
                        setOf(),
                        "example1"
                    )
                ),
                getScenarioAsTestReturning(
                    ScenarioMetadata(
                        "POST",
                        "/path2",
                        404,
                        setOf(),
                        setOf(),
                        "example2"
                    )
                )
            )
            val includeFilter = ScenarioMetadataFilter(methods = setOf("PUT"))
            val excludeFilter = ScenarioMetadataFilter(exampleNames = setOf("example3"))

            val result = filterUsing(tests, includeFilter, excludeFilter) {
                it.scenario.toScenarioMetadata()
            }.toList()

            assertEquals(emptyList<ContractTest>(), result.toList())
        }
    }

    @ParameterizedTest
    @MethodSource("provideMissingCriteriaScenariosForIsSatisfiedByAll")
    fun `should not satisfy all filters when any criteria is missing`(
        filter: ScenarioMetadataFilter,
        metadata: ScenarioMetadata,
        description: String
    ) {
        assertThat(filter.isSatisfiedByAll(metadata))
            .describedAs(description)
            .isFalse()
    }

    @ParameterizedTest
    @MethodSource("provideMatchingCriteriaScenariosForIsSatisfiedByAtLeastOne")
    fun `should satisfy at least one filter when any criteria matches`(
        filter: ScenarioMetadataFilter,
        metadata: ScenarioMetadata,
        description: String
    ) {
        assertThat(filter.isSatisfiedByAtLeastOne(metadata))
            .describedAs(description)
            .isTrue()
    }

    companion object {
        @JvmStatic
        fun provideMissingCriteriaScenariosForIsSatisfiedByAll(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("200"),
                        headers = setOf("Authorization"),
                        queryParams = setOf("productId"),
                        exampleNames = setOf("example2")
                    ),
                    ScenarioMetadata(
                        method = "POST",
                        path = "/products",
                        statusCode = 200,
                        header = setOf("X-Request-ID"), // Authorization header missing
                        query = setOf("productId"),
                        exampleName = "example2"
                    ),
                    "Missing Authorization header"
                ),
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("200"),
                        headers = setOf("Authorization"),
                        queryParams = setOf("productId"),
                        exampleNames = setOf("example2")
                    ),
                    ScenarioMetadata(
                        method = "POST",
                        path = "/products",
                        statusCode = 200,
                        header = setOf("Authorization"),
                        query = setOf(), // Query parameter productId missing
                        exampleName = "example2"
                    ),
                    "Missing required query parameter"
                ),
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("200"),
                        headers = setOf("Authorization"),
                        queryParams = setOf("productId"),
                        exampleNames = setOf("example2")
                    ),
                    ScenarioMetadata(
                        method = "GET", // Method does not match
                        path = "/products",
                        statusCode = 200,
                        header = setOf("Authorization"),
                        query = setOf("productId"),
                        exampleName = "example2"
                    ),
                    "Method does not match"
                ),
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("200"),
                        headers = setOf("Authorization"),
                        queryParams = setOf("productId"),
                        exampleNames = setOf("example2")
                    ),
                    ScenarioMetadata(
                        method = "POST",
                        path = "/products",
                        statusCode = 404, // Status code does not match
                        header = setOf("Authorization"),
                        query = setOf("productId"),
                        exampleName = "example2"
                    ),
                    "Status code does not match"
                ),
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("200"),
                        headers = setOf("Authorization"),
                        queryParams = setOf("productId"),
                        exampleNames = setOf("example2")
                    ),
                    ScenarioMetadata(
                        method = "POST",
                        path = "/products",
                        statusCode = 200,
                        header = setOf("Authorization"),
                        query = setOf("productId"),
                        exampleName = "" // Example name is missing
                    ),
                    "Example name is missing"
                )
            )
        }

        @JvmStatic
        fun provideMatchingCriteriaScenariosForIsSatisfiedByAtLeastOne(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("201")
                    ),
                    ScenarioMetadata(
                        method = "POST",
                        path = "/non-matching-path",
                        statusCode = 404,
                        header = emptySet(),
                        query = emptySet(),
                        exampleName = "non-matching"
                    ),
                    "Matching method should satisfy"
                ),
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("201")
                    ),
                    ScenarioMetadata(
                        method = "GET",
                        path = "/products",
                        statusCode = 200,
                        header = setOf("Authorization"),
                        query = setOf("productId"),
                        exampleName = "example1"
                    ),
                    "Matching path should satisfy"
                ),
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("201")
                    ),
                    ScenarioMetadata(
                        method = "PUT",
                        path = "/products",
                        statusCode = 201,
                        header = setOf("Authorization", "Content-Type"),
                        query = setOf("userId", "role"),
                        exampleName = "example2"
                    ),
                    "Matching status code should satisfy"
                ),
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("201"),
                        headers = setOf("X-Request-ID")
                    ),
                    ScenarioMetadata(
                        method = "PATCH",
                        path = "/non-matching-path",
                        statusCode = 400,
                        header = setOf("Authorization", "X-Request-ID"),
                        query = setOf("productId", "quantity"),
                        exampleName = "example3"
                    ),
                    "Matching header should satisfy"
                ),
                Arguments.of(
                    ScenarioMetadataFilter(
                        methods = setOf("POST"),
                        paths = setOf("/products"),
                        statusCodes = setOf("201")
                    ),
                    ScenarioMetadata(
                        method = "POST",
                        path = "/products",
                        statusCode = 201,
                        header = setOf("Authorization", "Content-Type"),
                        query = setOf("productId", "category"),
                        exampleName = "example4"
                    ),
                    "All criteria match"
                )
            )
        }
    }

}