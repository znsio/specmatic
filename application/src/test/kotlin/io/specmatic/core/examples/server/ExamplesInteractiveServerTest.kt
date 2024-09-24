package io.specmatic.core.examples.server

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.Dictionary
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
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

        @JvmStatic
        @AfterAll
        fun cleanUp() {
            val examplesFolder = File("src/test/resources/specifications/tracker_examples")
            if (examplesFolder.exists()) examplesFolder.delete()
        }
    }

    @Test
    fun `should generate all random values when no dictionary is provided`() {
        val examples = ExamplesInteractiveServer.generate(
            contractFile = File("src/test/resources/specifications/tracker.yaml"),
            scenarioFilter = ExamplesInteractiveServer.ScenarioFilter("", ""), extensive = false,
            externalDictionary = Dictionary()
        ).map { File(it) }

        try {
            examples.forEach {
                val example = ExampleFromFile(it)

                val request = example.request
                val requestBody = request.body as JSONObjectValue
                assertThat(request.headers).containsKeys("Authentication")
                assertThat(requestBody.findFirstChildByPath("name")?.toStringLiteral()).isNotNull()
                assertThat(requestBody.findFirstChildByPath("address")?.toStringLiteral()).isNotNull()

                val response = example.response
                val responseBody = (response.body as JSONArrayValue).list
                responseBody.forEach { value ->
                    value as JSONObjectValue
                    assertThat(value.findFirstChildByPath("name")?.toStringLiteral()).isNotNull()
                    assertThat(value.findFirstChildByPath("address")?.toStringLiteral()).isNotNull()
                }
            }
        } finally {
            examples.forEach { it.delete() }
        }
    }

    @Test
    fun `should use values from dictionary when provided`() {
        val examples = ExamplesInteractiveServer.generate(
            contractFile = File("src/test/resources/specifications/tracker.yaml"),
            scenarioFilter = ExamplesInteractiveServer.ScenarioFilter("", ""), extensive = false,
            externalDictionary = externalDictionary
        ).map { File(it) }

        try {
            examples.forEach {
                val example = ExampleFromFile(it)

                val request = example.request
                val requestBody = request.body as JSONObjectValue

                assertThat(request.headers).containsKeys("Authentication")
                assertThat(request.headers.getValue("Authentication")).isEqualTo("Bearer 123")
                assertThat(requestBody.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo("John Doe")
                assertThat(requestBody.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo("123 Main Street")

                val response = example.response
                val responseBody = (response.body as JSONArrayValue).list
                responseBody.forEachIndexed { index, value ->
                    value as JSONObjectValue
                    val (name, address) = when (index) {
                        0 -> "John Doe" to "123 Main Street"
                        else -> "Jane Doe" to "456 Main Street"
                    }

                    assertThat(value.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo(name)
                    assertThat(value.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo(address)
                }
            }
        } finally {
            examples.forEach { it.delete() }
        }
    }

    @Test
    fun `should only replace values if key is in dictionary`() {
        val examples = ExamplesInteractiveServer.generate(
            contractFile = File("src/test/resources/specifications/tracker.yaml"),
            scenarioFilter = ExamplesInteractiveServer.ScenarioFilter("", ""), extensive = false,
            externalDictionary = partialDictionary
        ).map { File(it) }

        try {
            examples.forEach {
                val example = ExampleFromFile(it)

                val request = example.request
                val requestBody = request.body as JSONObjectValue

                assertThat(request.headers).containsKeys("Authentication")
                assertThat(request.headers.getValue("Authentication")).isNotEqualTo("Bearer 123")
                assertThat(requestBody.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo("John Doe")
                assertThat(requestBody.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo("123 Main Street")

                val response = example.response
                val responseBody = (response.body as JSONArrayValue).list
                responseBody.forEachIndexed { index, value ->
                    value as JSONObjectValue
                    val (name, address) = when (index) {
                        0 -> "John Doe" to "123 Main Street"
                        else -> "Jane Doe" to "456 Main Street"
                    }

                    assertThat(value.findFirstChildByPath("name")?.toStringLiteral()).isNotEqualTo(name)
                    assertThat(value.findFirstChildByPath("address")?.toStringLiteral()).isNotEqualTo(address)
                }
            }
        } finally {
            examples.forEach { it.delete() }
        }
    }
}