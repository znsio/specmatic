package io.specmatic.core.examples.module

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.QueryParameters
import io.specmatic.core.SPECMATIC_STUB_DICTIONARY
import io.specmatic.core.examples.server.ExampleModule
import io.specmatic.core.examples.server.ScenarioFilter
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.utilities.Flags
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExampleGenerationModuleTest {
    companion object {
        private val externalDictionaryWithoutHeaders =
            parsedJSONObject(
                """
                {
                    "QUERY-PARAMS.name": "Jane Doe",
                    "QUERY-PARAMS.address": "123-Main-Street",
                    "PATH-PARAMS.name": "Jane-Doe",
                    "PATH-PARAMS.address": "123-Main-Street",
                    "Tracker.name": "Jane Doe",
                    "Tracker.address": "123-Main-Street",
                    "Tracker.trackerId": 100,
                    "Tracker_FVO.name": "Jane Doe",
                    "Tracker_FVO.address": "123-Main-Street"
                }
                """.trimIndent()
            )

        private val externalDictionary =
            parsedJSONObject(
                """
                {
                    "HEADERS.Authentication": "Bearer 123",
                    "QUERY-PARAMS.name": "Jane Doe",
                    "QUERY-PARAMS.address": "123-Main-Street",
                    "PATH-PARAMS.name": "Jane-Doe",
                    "PATH-PARAMS.address": "123-Main-Street",
                    "Tracker.name": "Jane Doe",
                    "Tracker.address": "123-Main-Street",
                    "Tracker.trackerId": 100,
                    "Tracker_FVO.name": "Jane Doe",
                    "Tracker_FVO.address": "123-Main-Street"
                }
                """.trimIndent()
            )

        fun assertHeaders(headers: Map<String, String>, apiKey: String) {
            assertThat(headers["Authentication"]).isEqualTo(apiKey)
        }

        fun assertPathParameters(path: String?, name: String, address: String) {
            assertThat(path).contains("/generate/names/$name/address/$address")
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
    }

    private val exampleGenerationModule = ExampleGenerationModule(ExampleModule())

    @BeforeEach
    fun resetCounter() {
        exampleGenerationModule.resetExampleFileNameCounter()
    }

    @Nested
    inner class DiscriminatorExamplesGenerationTests {
        private val specFile = File("src/test/resources/openapi/vehicle_deep_allof.yaml")
        private val examplesDir = specFile.parentFile.resolve("vehicle_deep_allof_examples")

        @AfterEach
        fun cleanUp() {
            if (examplesDir.exists()) {
                examplesDir.listFiles()?.forEach { it.delete() }
                examplesDir.delete()
            }
        }

        @Test
        fun `should generate multiple examples for top level discriminator`() {
            val generatedExamples = exampleGenerationModule.generate(
                contractFile = specFile,
                method = "POST", path = "/vehicles", responseStatusCode = 201,
                bulkMode = false, allowOnlyMandatoryKeysInJSONObject = false
            )

            assertThat(generatedExamples).hasSize(2)
            assertThat(generatedExamples).allSatisfy {
                assertThat(it.created).isTrue()
                assertThat(it.path).satisfiesAnyOf(
                    { path -> assertThat(path).contains("car") }, { path -> assertThat(path).contains("truck") }
                )
            }
        }

        @Test
        fun `should not generate multiple examples for deep nested discriminator`() {
            val generatedGetExamples = exampleGenerationModule.generate(
                contractFile = specFile,
                method = "GET", path = "/vehicles", responseStatusCode = 200,
                bulkMode = false, allowOnlyMandatoryKeysInJSONObject = false
            )

            assertThat(generatedGetExamples).hasSize(1)
            assertThat(generatedGetExamples.first().path).contains("GET")

            val generatedPatchExamples = exampleGenerationModule.generate(
                contractFile = specFile,
                method = "PATCH", path = "/vehicles", responseStatusCode = 200,
                bulkMode = false, allowOnlyMandatoryKeysInJSONObject = false
            )

            assertThat(generatedPatchExamples).hasSize(1)
            assertThat(generatedPatchExamples.first().path).contains("PATCH")
        }
    }

    @Nested
    inner class DictionaryExamplesGenerationTests {
        @AfterEach
        fun cleanUp() {
            val examplesFolder = File("src/test/resources/openapi/tracker_examples")
            if (examplesFolder.exists()) {
                examplesFolder.listFiles()?.forEach { it.delete() }
                examplesFolder.delete()
            }
        }

        @Test
        fun `should generate all random values when no dictionary is provided`() {
            val examples = exampleGenerationModule.generate(
                contractFile = File("src/test/resources/openapi/tracker.yaml"),
                scenarioFilter = ScenarioFilter("", ""),
                allowOnlyMandatoryKeysInJSONObject = false
            ).map { File(it) }

            examples.forEach {
                val example = ExampleFromFile(it)
                val request = example.request
                val response = example.response
                val responseBody = response.body as JSONArrayValue

                assertThat(request.headers["Authentication"])
                    .withFailMessage("Header values should be randomly generated")
                    .isNotEqualTo("Bearer 123")

                when (request.method) {
                    "POST" -> {
                        val body = request.body as JSONObjectValue
                        assertThat(body.findFirstChildByPath("name")?.toStringLiteral()).isNotEqualTo("John-Doe")
                        assertThat(
                            body.findFirstChildByPath("address")?.toStringLiteral()
                        ).isNotEqualTo("123-Main-Street")

                    }

                    "GET" -> {
                        val queryParameters = request.queryParams
                        assertThat(queryParameters.getValues("name")).doesNotContain("John-Doe")
                        assertThat(queryParameters.getValues("address")).doesNotContain("123-Main-Street")
                    }

                    "DELETE" -> {
                        val path = request.path as String
                        assertThat(path).doesNotContain("/generate/names/John-Doe/address/123-Main-Street")
                        assertThat(path.trim('/').split('/').last()).isNotEqualTo("(string)")
                    }

                    else -> throw IllegalArgumentException("Unexpected method ${request.method}")
                }

                responseBody.list.forEachIndexed { index, value ->
                    value as JSONObjectValue
                    val (name, address) = when (index) {
                        0 -> "John Doe" to "123 Main Street"
                        else -> "Jane Doe" to "456 Main Street"
                    }

                    assertThat(value.findFirstChildByPath("name")?.toStringLiteral()).isNotEqualTo(name)
                    assertThat(value.findFirstChildByPath("address")?.toStringLiteral()).isNotEqualTo(address)
                }
            }
        }

        @Test
        fun `should use values from dictionary when provided`(@TempDir tempDir: File) {
            val dictionaryFileName = "dictionary.json"

            val dictionaryFile = tempDir.resolve(dictionaryFileName)
            dictionaryFile.writeText(externalDictionary.toStringLiteral())

            val examples = Flags.using(SPECMATIC_STUB_DICTIONARY to dictionaryFile.path) {
                exampleGenerationModule.generate(
                    contractFile = File("src/test/resources/openapi/tracker.yaml"),
                    scenarioFilter = ScenarioFilter("", ""),
                    allowOnlyMandatoryKeysInJSONObject = false
                ).map { File(it) }
            }

            examples.forEach {
                val example = ExampleFromFile(it)
                val request = example.request
                val response = example.response

                assertHeaders(request.headers, "Bearer 123")

                when (request.method) {
                    "POST" -> assertRequestBody(request.body, "Jane Doe", "123-Main-Street")
                    "GET" -> assertQueryParameters(request.queryParams, "Jane Doe", "123-Main-Street")
                    "DELETE" -> {
                        assertPathParameters(request.path, "Jane-Doe", "123-Main-Street")
                        assertThat(request.path!!.trim('/').split('/').last()).isNotEqualTo("(string)")
                    }

                    else -> throw IllegalArgumentException("Unexpected method ${request.method}")
                }

                val jsonResponseBody = response.body as JSONArrayValue
                assertThat(jsonResponseBody.list).allSatisfy {
                    it as JSONObjectValue

                    assertThat(it.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo("Jane Doe")
                    assertThat(it.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo("123-Main-Street")
                }
            }
        }

        @Test
        fun `should only replace values if key is in dictionary`(@TempDir tempDir: File) {
            val dictionaryFileName = "dictionary.json"

            val dictionaryFile = tempDir.resolve(dictionaryFileName)
            dictionaryFile.writeText(externalDictionaryWithoutHeaders.toStringLiteral())

            val examples = Flags.using(SPECMATIC_STUB_DICTIONARY to dictionaryFile.path) {
                exampleGenerationModule.generate(
                    contractFile = File("src/test/resources/openapi/tracker.yaml"),
                    scenarioFilter = ScenarioFilter("", ""),
                    allowOnlyMandatoryKeysInJSONObject = false
                ).map { File(it) }
            }

            examples.forEach {
                val example = ExampleFromFile(it)
                val request = example.request
                val response = example.response
                val responseBody = response.body as JSONArrayValue

                assertThat(request.headers["Authentication"])
                    .withFailMessage("Header values should be randomly generated")
                    .isNotEqualTo("Bearer 123")

                when (request.method) {
                    "POST" -> assertRequestBody(request.body, "Jane Doe", "123-Main-Street")
                    "GET" -> assertQueryParameters(request.queryParams, "Jane Doe", "123-Main-Street")
                    "DELETE" -> {
                        assertPathParameters(request.path, "Jane-Doe", "123-Main-Street")
                        assertThat(request.path!!.trim('/').split('/').last()).isNotEqualTo("(string)")
                    }

                    else -> throw IllegalArgumentException("Unexpected method ${request.method}")
                }

                assertThat(responseBody.list).allSatisfy { value ->
                    value as JSONObjectValue

                    assertThat(value.findFirstChildByPath("name")?.toStringLiteral()).isEqualTo("Jane Doe")
                    assertThat(value.findFirstChildByPath("address")?.toStringLiteral()).isEqualTo("123-Main-Street")
                }
            }
        }
    }

}