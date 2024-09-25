package io.specmatic.core.examples.server

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.Dictionary
import io.specmatic.core.HttpRequest
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

        fun assertTrackerRequest(httpRequest: HttpRequest, name: String, address: String) {
            when(httpRequest.method) {
                "POST" -> {
                    val requestBody = httpRequest.body as JSONObjectValue
                    assertThat(requestBody.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo(name)
                    assertThat(requestBody.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo(address)
                }

                "GET" -> {
                    val queryParams = httpRequest.queryParams
                    assertThat(queryParams.getValues("name")).contains(name)
                    assertThat(queryParams.getValues("address")).contains(address)
                }
            }
        }

        fun assertTrackerResponseObject(value: Value, name: String, address: String) {
            value as JSONObjectValue
            assertThat(value.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo(name)
            assertThat(value.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo(address)
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
            assertThat(request.headers).containsKeys("Authentication")
            assertThrows<AssertionError>("Name and Address should be random Values") {
                assertTrackerRequest(request, "John Doe", "123 Main Street")
            }

            val response = example.response
            val responseBody = (response.body as JSONArrayValue).list
            responseBody.forEach { value ->
                assertThrows<AssertionError> {
                    assertTrackerResponseObject(value, "John Doe", "123 Main Street")
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
            assertThat(request.headers.getValue("Authentication")).isEqualTo("Bearer 123")
            assertTrackerRequest(request, "John Doe", "123 Main Street")

            val response = example.response
            val responseBody = (response.body as JSONArrayValue).list
            responseBody.forEachIndexed { index, value ->
                val (name, address) = when (index) {
                    0 -> "John Doe" to "123 Main Street"
                    else -> "Jane Doe" to "456 Main Street"
                }
                assertTrackerResponseObject(value, name, address)
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
            assertThat(request.headers).containsKeys("Authentication")
            assertTrackerRequest(request, "John Doe", "123 Main Street")

            val response = example.response
            val responseBody = (response.body as JSONArrayValue).list
            responseBody.forEachIndexed { index, value ->
                val (name, address) = when (index) {
                    0 -> "John Doe" to "123 Main Street"
                    else -> "Jane Doe" to "456 Main Street"
                }

                assertThrows<AssertionError>("Name and Address should be random Values") {
                    assertTrackerResponseObject(value, name, address)
                }
            }
        }
    }
}