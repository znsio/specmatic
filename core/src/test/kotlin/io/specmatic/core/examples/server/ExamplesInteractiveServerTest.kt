package io.specmatic.core.examples.server

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class ExamplesInteractiveServerTest {
    @BeforeEach
    fun resetCounter() {
        ExamplesInteractiveServer.resetExampleFileNameCounter()
    }

    @Nested
    inner class ExternaliseInlineExamplesTests {

        @Test
        fun `should externalise the inline examples into an examples folder`() {
            var examplesDir: File? = null
            try {
                val contractFile = File("src/test/resources/openapi/has_multiple_inline_examples.yaml")
                examplesDir = ExamplesInteractiveServer.externaliseInlineExamples(contractFile)

                val externalisedExampleFiles = examplesDir.listFiles()?.map { it.name }
                assertThat(externalisedExampleFiles).containsExactlyInAnyOrder(
                    "products_POST_201_1.json",
                    "findAvailableProducts_GET_200_2.json",
                    "findAvailableProducts_GET_503_3.json",
                    "orders_GET_200_4.json"
                )
                val postProductsExample = examplesDir.listFiles()?.first {
                    val exampleFromFile = ExampleFromFile(it)
                    exampleFromFile.request.path == "/products" && exampleFromFile.requestMethod == "POST"
                }

                val postProductsExampleFromFile = ExampleFromFile(postProductsExample!!)
                assertThat(postProductsExampleFromFile.request).isEqualTo(
                    HttpRequest(
                        method = "POST",
                        path = "/products",
                        headers = mapOf("Content-Type" to "application/json"),
                        body = JSONObjectValue(
                            mapOf(
                                "name" to StringValue("iPhone"),
                                "type" to StringValue("gadget"),
                                "inventory" to NumberValue(100)
                            )
                        )
                    )
                )
                assertThat(postProductsExampleFromFile.response).isEqualTo(
                    HttpResponse(
                        status = 201,
                        body = JSONObjectValue(
                            mapOf(
                                "id" to NumberValue(1)
                            )
                        ),
                        headers = emptyMap()
                    )
                )
            } finally {
                examplesDir?.deleteRecursively()
            }
        }
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
            val generatedExamples = ExamplesInteractiveServer.generate(
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
            val generatedGetExamples = ExamplesInteractiveServer.generate(
                contractFile = specFile,
                method = "GET", path = "/vehicles", responseStatusCode = 200,
                bulkMode = false, allowOnlyMandatoryKeysInJSONObject = false
            )

            assertThat(generatedGetExamples).hasSize(1)
            assertThat(generatedGetExamples.first().path).contains("GET")

            val generatedPatchExamples = ExamplesInteractiveServer.generate(
                contractFile = specFile,
                method = "PATCH", path = "/vehicles", responseStatusCode = 200,
                bulkMode = false, allowOnlyMandatoryKeysInJSONObject = false
            )

            assertThat(generatedPatchExamples).hasSize(1)
            assertThat(generatedPatchExamples.first().path).contains("PATCH")
        }
    }
}