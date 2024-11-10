package io.specmatic.core

import io.specmatic.conversions.OpenApiSpecification
import io.mockk.every
import io.mockk.mockk
import io.specmatic.core.pattern.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class ScenarioTest {
    @Test
    fun `should validate and reject an invalid response in an example row`() {
        val responseExample = HttpResponse(200, """{"id": "abc123"}""")
        val scenario = Scenario(
            "",
            HttpRequestPattern(
                method = "POST",
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            emptyMap(),
            listOf(
                Examples(
                    listOf("(REQUEST-BODY)"),
                    listOf(Row(
                        mapOf("(REQUEST-BODY)" to """{"id": 10}""")
                    ).copy(
                        responseExample = responseExample
                    ))
                )
            ),
            emptyMap(),
            emptyMap()
        )

        assertThatThrownBy {
            scenario.validExamplesOrException(DefaultStrategies)
        }.satisfies(Consumer {
            assertThat(it)
                .hasMessageContaining("abc123")
                .hasMessageContaining("RESPONSE.BODY.id")
        })
    }

    @Test
    fun `should validate and reject an invalid request in an example row`() {
        val scenario = Scenario(
            "",
            HttpRequestPattern(
                method = "POST",
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            emptyMap(),
            listOf(
                Examples(
                    listOf("(REQUEST-BODY)"),
                    listOf(Row(
                        mapOf("(REQUEST-BODY)" to """{"id": "abc123" }""")
                    ).copy(responseExample = HttpResponse(200, """{"id": 10}""")))
                )
            ),
            emptyMap(),
            emptyMap()
        )

        assertThatThrownBy {
            scenario.validExamplesOrException(DefaultStrategies)
        }.satisfies(Consumer {
            assertThat(it)
                .hasMessageContaining("abc123")
                .hasMessageContaining("REQUEST.BODY.id")
        })
    }

    @Test
    fun `should validate and accept a response in an example row with pattern specification as value`() {
        val scenario = Scenario(
            "",
            HttpRequestPattern(
                method = "POST",
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            emptyMap(),
            listOf(
                Examples(
                    listOf("(REQUEST-BODY)"),
                    listOf(Row(
                        mapOf("(REQUEST-BODY)" to """{"id": 10}""")
                    ).copy(responseExample = HttpResponse(200, """{"id": "(number)"}""")))
                )
            ),
            emptyMap(),
            emptyMap()
        )

        assertThatCode {
            scenario.validExamplesOrException(DefaultStrategies)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `should validate and accept a request in an example row  with pattern specification as value`() {
        val scenario = Scenario(
            "",
            HttpRequestPattern(
                method = "POST",
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            emptyMap(),
            listOf(
                Examples(
                    listOf("(REQUEST-BODY)"),
                    listOf(Row(
                        mapOf("(REQUEST-BODY)" to """{"id": "(number)" }""")
                    ))
                )
            ),
            emptyMap(),
            emptyMap()
        )

        assertThatCode {
            scenario.validExamplesOrException(DefaultStrategies)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `should validate and reject a response in an example row with a non-matching pattern specification as value`() {
        val responseExample = HttpResponse(200, """{"id": "(string)"}""")
        val scenario = Scenario(
            "",
            HttpRequestPattern(
                method = "POST",
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            emptyMap(),
            listOf(
                Examples(
                    listOf("(REQUEST-BODY)"),
                    listOf(Row(
                        mapOf("(REQUEST-BODY)" to """{"id": 10}""")
                    ).copy(
                        responseExample = responseExample
                    ))
                )
            ),
            emptyMap(),
            emptyMap()
        )

        assertThatCode {
            scenario.validExamplesOrException(DefaultStrategies)
        }.satisfies(Consumer {
            assertThat(it)
                .hasMessageContaining("string")
                .hasMessageContaining("RESPONSE.BODY.id")
        })
    }

    @Test
    fun `should validate and accept a request in an example row  with a non-matching pattern specification as value`() {
        val scenario = Scenario(
            "",
            HttpRequestPattern(
                method = "POST",
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            emptyMap(),
            listOf(
                Examples(
                    listOf("(REQUEST-BODY)"),
                    listOf(Row(
                        mapOf("(REQUEST-BODY)" to """{"id": "(string)" }""")
                    ))
                )
            ),
            emptyMap(),
            emptyMap()
        )

        assertThatThrownBy {
            scenario.validExamplesOrException(DefaultStrategies)
        }.satisfies(Consumer {
            assertThat(it)
                .hasMessageContaining("string")
                .hasMessageContaining("REQUEST.BODY.id")
        })
    }

    @Test
    fun `api description is as per the format required by the workflow feature`() {
        val scenario = Scenario(
            "",
            HttpRequestPattern(
                method = "POST",
                httpPathPattern = buildHttpPathPattern("/products"),
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            HttpResponsePattern(
                status = 201,
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "id" to NumberPattern()
                    )
                )
            ),
            emptyMap(),
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        assertThat(scenario.apiDescription).isEqualTo("POST /products -> 201")
    }

    @Test
    fun `should return scenarioMetadata from scenario`() {
        val httpRequestPattern = mockk<HttpRequestPattern> {
            every {
                getHeaderKeys()
            } returns setOf("Authorization", "X-Request-ID")

            every {
                getQueryParamKeys()
            } returns setOf("productId", "orderId")

            every { method } returns "POST"
            every { httpPathPattern } returns HttpPathPattern(emptyList(), "/createProduct")
        }

        val scenarioMetadata = Scenario(
            "",
            httpRequestPattern,
            HttpResponsePattern(status = 200),
            exampleName = "example"
        ).toScenarioMetadata()

        assertThat(scenarioMetadata.method).isEqualTo("POST")
        assertThat(scenarioMetadata.path).isEqualTo("/createProduct")
        assertThat(scenarioMetadata.query).isEqualTo(setOf("productId", "orderId"))
        assertThat(scenarioMetadata.header).isEqualTo(setOf("Authorization", "X-Request-ID"))
        assertThat(scenarioMetadata.statusCode).isEqualTo(200)
        assertThat(scenarioMetadata.exampleName).isEqualTo("example")
    }

    @Nested
    inner class AttributeSelectionTest {
        @Test
        fun `should validate unexpected keys when the request is attribute based`() {
            val httpRequestPattern = HttpRequestPattern(
                httpPathPattern = buildHttpPathPattern("/test"),
                method = "GET",
                httpQueryParamPattern = HttpQueryParamPattern(
                    queryPatterns = mapOf("fields?" to ListPattern(StringPattern()))
                )
            )

            val httpResponsePattern = HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(
                    pattern = mapOf("id" to NumberPattern(), "name" to StringPattern())
                )
            )

            val scenario = Scenario(
                "",
                httpRequestPattern,
                httpResponsePattern,
                attributeSelectionPattern = AttributeSelectionPattern(queryParamKey = "fields", defaultFields = emptyList())
            )

            val httpRequest = HttpRequest(
                path = "/test",
                method = "GET",
                queryParams = QueryParameters(mapOf("fields" to "id"))
            )

            val httpResponse = HttpResponse(
                status = 200,
                body = """{"id": 10, "extraKey": "extraValue"}"""
            )

            val flagBasedWithExtensibleSchema = DefaultStrategies.copy(unexpectedKeyCheck = IgnoreUnexpectedKeys)
            val result = scenario.matches(httpRequest, httpResponse, DefaultMismatchMessages, flagBasedWithExtensibleSchema)

            println(result.reportString())
            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat(result.reportString()).containsIgnoringWhitespaces("""
            >> RESPONSE.BODY.extraKey 
            Key named "extraKey" was unexpected
            """.trimIndent())
        }

        @Test
        fun `should fallback to flag based when the request is not attribute based`() {
            val httpRequestPattern = HttpRequestPattern(
                httpPathPattern = buildHttpPathPattern("/test"),
                method = "GET",
                httpQueryParamPattern = HttpQueryParamPattern(
                    queryPatterns = mapOf("fields?" to ListPattern(StringPattern()))
                )
            )

            val httpResponsePattern = HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(
                    pattern = mapOf("id" to NumberPattern(), "name" to StringPattern())
                )
            )

            val scenario = Scenario(
                "",
                httpRequestPattern,
                httpResponsePattern,
                attributeSelectionPattern = AttributeSelectionPattern(queryParamKey = "fields", defaultFields = emptyList())
            )

            val httpRequest = HttpRequest(
                path = "/test",
                method = "GET"
            )

            val httpResponse = HttpResponse(
                status = 200,
                body = """{"id": 10, "name": "name", "extraKey": "extraValue"}"""
            )

            val flagBasedWithExtensibleSchema = DefaultStrategies.copy(unexpectedKeyCheck = IgnoreUnexpectedKeys)
            val extensibleResult = scenario.matches(httpRequest, httpResponse, DefaultMismatchMessages, flagBasedWithExtensibleSchema)

            println(extensibleResult.reportString())
            assertThat(extensibleResult).isInstanceOf(Result.Success::class.java)

            val flagBasedWithoutExtensibleSchema = DefaultStrategies
            val nonExtensibleResult = scenario.matches(httpRequest, httpResponse, DefaultMismatchMessages, flagBasedWithoutExtensibleSchema)

            println(nonExtensibleResult.reportString())
            assertThat(nonExtensibleResult).isInstanceOf(Result.Failure::class.java)
            assertThat(nonExtensibleResult.reportString()).containsIgnoringWhitespaces("""
            >> RESPONSE.BODY.extraKey 
            Key named "extraKey" was unexpected
            """.trimIndent())
        }
    }
}