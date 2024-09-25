package io.specmatic.core.examples.server

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.Dictionary
import io.specmatic.core.QueryParameters
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ExamplesInteractiveServerTest {
    companion object {
        private val externalDictionary = Dictionary(
            parsedJSONObject("""
                {
                    "Authentication": "Bearer 123",
                    "name": "John Doe",
                    "address": "123 Main Street",
                    "[0].name": "John Doe",
                    "[0].address": "123 Main Street",
                    "[*].name": "Jane Doe",
                    "[*].address": "456 Main Street"
                }
                """.trimIndent()).jsonObject
        )
        private val partialDictionary = Dictionary(
            parsedJSONObject("""
                {
                    "name": "John Doe",
                    "address": "123 Main Street"
                }
                """.trimIndent()).jsonObject
        )

        fun assertHeaders(headers: Map<String, String>, apiKey: String) {
            assertThat(headers["Authentication"]).isEqualTo(apiKey)
        }

        fun assertQueryParameters(queryParameters: QueryParameters, name: String, address: String) {
            assertThat(queryParameters.getValues("name")).contains(name)
            assertThat(queryParameters.getValues("address")).contains(address)
        }

        fun assertRequestBody(body: Value, name: String, address: String) {
            body as JSONObjectValue
            assertThat(body.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo(name)
            assertThat(body.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo(address)
        }

        fun assertResponseBody(body: Value, getNameAddress: (index: Int) -> Pair<String, String>) {
            body as JSONArrayValue
            body.list.forEachIndexed { index, value ->
                value as JSONObjectValue

                val (name, address) = getNameAddress(index)
                assertThat(value.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo(name)
                assertThat(value.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo(address)
            }
        }
    }

    @AfterEach
    fun cleanUp() {
        val examplesFolder = File("src/test/resources/specifications/tracker_examples")
        if (examplesFolder.exists()) {
            examplesFolder.listFiles()?.forEach { it.delete() }
            examplesFolder.delete()
        }
    }

    @Test
    fun `should generate all random values when no dictionary is provided`() {
        val examples = ExamplesInteractiveServer.generate(
            contractFile = File("src/test/resources/specifications/tracker.yaml"),
            scenarioFilter = ExamplesInteractiveServer.ScenarioFilter("", ""), extensive = false,
            externalDictionary = Dictionary()
        ).map { File(it) }

        examples.forEach {
            val example = ExampleFromFile(it)
            val request = example.request
            val response = example.response

            assertThrows<AssertionError>("Header Values should be randomly generated") {
                assertHeaders(request.headers, "Bearer 123")
            }

            assertThrows<AssertionError>("Request Body Values should be randomly generated") {
                when(request.method) {
                    "POST" -> assertRequestBody(request.body, "John Doe", "123 Main Street")
                    "GET"  -> assertQueryParameters(request.queryParams, "John Doe", "123 Main Street")
                }
            }

            assertThrows<AssertionError>("Response Body Values should be randomly generated") {
                assertResponseBody(response.body) {
                    index -> when(index) {
                        0 -> "John Doe" to "123 Main Street"
                        else -> "Jane Doe" to "456 Main Street"
                    }
                }
            }
        }
    }

    @Test
    fun `should use values from dictionary when provided`() {
        val examples = ExamplesInteractiveServer.generate(
            contractFile = File("src/test/resources/specifications/tracker.yaml"),
            scenarioFilter = ExamplesInteractiveServer.ScenarioFilter("", ""), extensive = false,
            externalDictionary = externalDictionary
        ).map { File(it) }

        examples.forEach {
            val example = ExampleFromFile(it)
            val request = example.request
            val response = example.response

            assertHeaders(request.headers, "Bearer 123")

            when(request.method) {
                "POST" -> assertRequestBody(request.body, "John Doe", "123 Main Street")
                "GET"  -> assertQueryParameters(request.queryParams, "John Doe", "123 Main Street")
            }

            assertResponseBody(response.body) {
                index -> when(index) {
                    0 -> "John Doe" to "123 Main Street"
                    else -> "Jane Doe" to "456 Main Street"
                }
            }
        }
    }

    @Test
    fun `should only replace values if key is in dictionary`() {
        val examples = ExamplesInteractiveServer.generate(
            contractFile = File("src/test/resources/specifications/tracker.yaml"),
            scenarioFilter = ExamplesInteractiveServer.ScenarioFilter("", ""), extensive = false,
            externalDictionary = partialDictionary
        ).map { File(it) }

        examples.forEach {
            val example = ExampleFromFile(it)
            val request = example.request
            val response = example.response

            assertThrows<AssertionError>("Header Values should be randomly generated") {
                assertHeaders(request.headers, "Bearer 123")
            }

            when(request.method) {
                "POST" -> assertRequestBody(request.body, "John Doe", "123 Main Street")
                "GET"  -> assertQueryParameters(request.queryParams, "John Doe", "123 Main Street")
            }

            assertThrows<AssertionError>("Response Body Values should be randomly generated") {
                assertResponseBody(response.body) {
                    index -> when(index) {
                        0 -> "John Doe" to "123 Main Street"
                        else -> "Jane Doe" to "456 Main Street"
                    }
                }
            }
        }
    }
}