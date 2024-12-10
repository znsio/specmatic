package io.specmatic.core.examples.server

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.utilities.Flags.Companion.ALL_PATTERNS_MANDATORY
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.specmatic.mock.ScenarioStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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

    @Nested
    inner class AllPatternsMandatoryTests {
        private val spec = """
        openapi: 3.0.0
        info:
          title: test
          version: 1.0.0
        paths:
          /products:
            post:
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      ${'$'}ref: '#/components/schemas/ProductRequest'
              responses:
                200:
                  description: OK
                  content:
                    application/json:
                      schema:
                        type: array
                        items:
                          ${'$'}ref: '#/components/schemas/Product'
        components:
          schemas:
            ProductRequest:
              type: object
              properties:
                name:
                  type: string
                type:
                  type: string
                inventory:
                  type: number
              required:
                - name
            Product:
              type: object
              properties:
                id:
                  type: number
                name:
                  type: string
                type:
                  type: string
                inventory:
                  type: number
              required:
                - id
                - name
            """.trimIndent()

        @BeforeEach
        fun setup() {
            System.setProperty(ALL_PATTERNS_MANDATORY, "true")
        }

        @AfterEach
        fun reset() {
            System.clearProperty(ALL_PATTERNS_MANDATORY)
        }

        @Test
        fun `should warn about missing optional keys`(@TempDir tempDir: File) {
            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val exampleFile = tempDir.resolve("example.json")
            val example = ScenarioStub(
                request = HttpRequest(path = "/products", method = "POST", body = JSONObjectValue(mapOf("name" to StringValue("iPhone")))),
                response = HttpResponse(status = 200, body = JSONArrayValue(
                    List(2) { JSONObjectValue(mapOf("id" to NumberValue(1), "name" to StringValue("iPhone")))}
                ))
            )
            exampleFile.writeText(example.toJSON().toStringLiteral())

            val result = ExamplesInteractiveServer.validateSingleExample(feature, exampleFile)
            println(result.reportString())

            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat(result.isPartialFailure()).isTrue()
            assertThat(result.reportString()).containsIgnoringWhitespaces("""
            >> REQUEST.BODY.type
            Optional Key type in the specification is missing from the example
            >> REQUEST.BODY.inventory
            Optional Key inventory in the specification is missing from the example
            
            >> RESPONSE.BODY[0].type
            Optional Key type in the specification is missing from the example
            >> RESPONSE.BODY[0].inventory
            Optional Key inventory in the specification is missing from the example

            >> RESPONSE.BODY[1].type
            Optional Key type in the specification is missing from the example
            >> RESPONSE.BODY[1].inventory
            Optional Key inventory in the specification is missing from the example
            """.trimIndent())
        }

        @Test
        fun `should not be partial failure when mandatory key is missing`(@TempDir tempDir: File) {
            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val exampleFile = tempDir.resolve("example.json")
            val example = ScenarioStub(
                request = HttpRequest(path = "/products", method = "POST", body = JSONObjectValue(mapOf("name" to StringValue("iPhone")))),
                response = HttpResponse(status = 200, body = JSONArrayValue(
                    List(1) { JSONObjectValue(mapOf("id" to NumberValue(1))) }
                ))
            )
            exampleFile.writeText(example.toJSON().toStringLiteral())

            val result = ExamplesInteractiveServer.validateSingleExample(feature, exampleFile)
            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat(result.isPartialFailure()).isFalse()

            val report = result.reportString()
            println(report)
            assertThat(report).containsIgnoringWhitespaces("""
            >> REQUEST.BODY.type
            Optional Key type in the specification is missing from the example
            >> REQUEST.BODY.inventory
            Optional Key inventory in the specification is missing from the example

            >> RESPONSE.BODY[0].name
            Key name in the specification is missing from the example
            >> RESPONSE.BODY[0].type
            Optional Key type in the specification is missing from the example
            >> RESPONSE.BODY[0].inventory
            Optional Key inventory in the specification is missing from the example
            """.trimIndent())
        }

        @Test
        fun `should not be partial failure when type mismatch`(@TempDir tempDir: File) {
            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val exampleFile = tempDir.resolve("example.json")
            val example = ScenarioStub(
                request = HttpRequest(path = "/products", method = "POST", body = JSONObjectValue(
                    mapOf("name" to StringValue("iPhone"), "type" to StringValue("phone"), "inventory" to NumberValue(1))
                )),
                response = HttpResponse(status = 200, body = JSONArrayValue(
                    List(2) { JSONObjectValue(mapOf("id" to NumberValue(1), "name" to NumberValue(123))) }
                ))
            )
            exampleFile.writeText(example.toJSON().toStringLiteral())

            val result = ExamplesInteractiveServer.validateSingleExample(feature, exampleFile)
            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat(result.isPartialFailure()).isFalse()

            val report = result.reportString()
            println(report)
            assertThat(report).containsIgnoringWhitespaces("""
            >> RESPONSE.BODY[0].type
            Optional Key type in the specification is missing from the example
            >> RESPONSE.BODY[0].inventory
            Optional Key inventory in the specification is missing from the example
            >> RESPONSE.BODY[0].name
            Specification expected string but example contained 123 (number)
            
            >> RESPONSE.BODY[1].type
            Optional Key type in the specification is missing from the example
            >> RESPONSE.BODY[1].inventory
            Optional Key inventory in the specification is missing from the example
            >> RESPONSE.BODY[1].name
            Specification expected string but example contained 123 (number)
            """.trimIndent())
        }

        @Test
        fun `should not complain about missing optional keys when all patterns mandatory is false`(@TempDir tempDir: File) {
            System.setProperty(ALL_PATTERNS_MANDATORY, "false")
            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val exampleFile = tempDir.resolve("example.json")
            val example = ScenarioStub(
                request = HttpRequest(path = "/products", method = "POST", body = JSONObjectValue(mapOf("name" to StringValue("iPhone")))),
                response = HttpResponse(status = 200, body = JSONArrayValue(
                    List(2) { JSONObjectValue(mapOf("id" to NumberValue(1), "name" to StringValue("iPhone")))}
                ))
            )
            exampleFile.writeText(example.toJSON().toStringLiteral())

            val result = ExamplesInteractiveServer.validateSingleExample(feature, exampleFile)
            println(result.reportString())

            assertThat(result).isInstanceOf(Result.Success::class.java)
            assertThat(result.reportString()).isEmpty()
        }
    }
}