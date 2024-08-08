package io.specmatic.core

import io.specmatic.core.pattern.*
import org.assertj.core.api.Assertions.*
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
            listOf<Examples>(
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
            listOf<Examples>(
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
            listOf<Examples>(
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
            listOf<Examples>(
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
            listOf<Examples>(
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
            listOf<Examples>(
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
}