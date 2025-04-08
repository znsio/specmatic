package io.specmatic.conversions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.specmatic.core.*
import io.specmatic.core.pattern.DeferredPattern
import io.specmatic.core.pattern.EnumPattern
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OpenApiPreProcessorTest {

    companion object {
        private fun assertYamlEquals(expected: String, actual: String) {
            val expectedValue = yamlMapper.readValue(expected, Map::class.java)
            val actualValue = yamlMapper.readValue(actual, Map::class.java)
            assertThat(actualValue).isEqualTo(expectedValue)
        }

        private val yamlMapper = ObjectMapper(YAMLFactory())
        private val mapper = ObjectMapper()
        private val currentFile = File(".")
    }

    @Test
    fun `should be able to to load open-api specification with referenced paths`() {
        val openApiFile = File("src/test/resources/openapi/has_referenced_paths/api/v1/order.yaml")
        val specification = OpenApiSpecification.fromFile(openApiFile.canonicalPath)
        val feature = specification.toFeature()

        assertThat(feature.scenarios).hasSize(1).containsExactly(
            Scenario(ScenarioInfo(
                scenarioName = "GET /v1/products. Response: successful operation",
                httpRequestPattern = HttpRequestPattern(httpPathPattern = HttpPathPattern.from("/v1/products"), method = "GET", body = NoBodyPattern),
                httpResponsePattern = HttpResponsePattern(
                    status = 200,
                    headersPattern = HttpHeadersPattern(contentType = "application/json"),
                    body = DeferredPattern("(Product)")
                ),
                patterns = mapOf(
                    "(ProductType)" to EnumPattern(values = setOf("FOOD", "GADGET", "OTHER").map { StringValue(it) }, typeAlias = "ProductType"),
                    "(Product)" to JSONObjectPattern(mapOf("category?" to DeferredPattern("(ProductType)")), typeAlias = "(Product)")
                ),
                serviceType = "HTTP"
            ))
        )
    }

    @Test
    fun `should not modify non referenced paths in open-api specification`() {
        val openApiSpecification = """
        openapi: 3.0.3
        info:
          title: Check Health
          version: 1.0.0
        paths:
          /check:
            get:
              summary: Check Health
              responses:
                '200':
                  description: successful operation
        """.trimIndent()
        val preProcessor = OpenApiPreProcessor(openApiSpecification, currentFile.canonicalPath)
        val processedOpenApiYamlContent = preProcessor.inlinePathReferences().toYAML()

        assertYamlEquals(openApiSpecification, processedOpenApiYamlContent)
    }

    @Test
    fun `should work with open api specification in json format`(@TempDir tempDir: File) {
        val source = File("src/test/resources/openapi/has_referenced_paths")
        source.copyRecursively(tempDir, true)
        convertYamlToJsonRecursively(tempDir)

        val openApiFile = File(tempDir, "api/v1/order.json")
        val specification = OpenApiSpecification.fromFile(openApiFile.canonicalPath)
        val feature = specification.toFeature()

        assertThat(feature.scenarios).hasSize(1).containsExactly(Scenario(ScenarioInfo(
            scenarioName = "GET /v1/products. Response: successful operation",
            httpRequestPattern = HttpRequestPattern(httpPathPattern = HttpPathPattern.from("/v1/products"), method = "GET", body = NoBodyPattern),
            httpResponsePattern = HttpResponsePattern(
                status = 200,
                headersPattern = HttpHeadersPattern(contentType = "application/json"),
                body = DeferredPattern("(Product)")
            ),
            patterns = mapOf(
                "(ProductType)" to EnumPattern(values = setOf("FOOD", "GADGET", "OTHER").map { StringValue(it) }, typeAlias = "ProductType"),
                "(Product)" to JSONObjectPattern(mapOf("category?" to DeferredPattern("(ProductType)")), typeAlias = "(Product)")
            ),
            serviceType = "HTTP"
        )))
    }

    @Test
    fun `should not modify absolute or http references in open-api specification`(@TempDir tempDir: File) {
        val specification = """
        openapi: 3.0.3
        info:
          title: Check Health
          version: 1.0.0
        paths:
          /check:
            ${"$"}ref: ${tempDir.resolve("paths/check.yaml").canonicalPath} 
        """.trimIndent()
        val checksSpecification = """
        get:
          summary: Check Health
          responses:
            '200':
              description: successful operation
              content:
                application/json:
                  schema:
                    ${"$"}ref: ${tempDir.resolve("components/schemas/Health.yaml").canonicalPath}
                text/plain:
                  schema:
                    ${"$"}ref: http://example.com/components/schemas/Health.yaml
        """.trimIndent()
        val healthSchema = """
        type: object
        properties:
          status:
            type: string
        """.trimIndent()

        File(tempDir, "paths").apply { mkdirs() }
        File(tempDir, "paths/check.yaml").writeText(checksSpecification)

        File(tempDir, "components/schemas").apply { mkdirs() }
        File(tempDir, "components/schemas/Health.yaml").writeText(healthSchema)

        val openApiSpec = File(tempDir, "api.yaml").apply { writeText(specification) }
        val processedApiSpec = OpenApiPreProcessor(openApiSpec.readText(), openApiSpec.canonicalPath).inlinePathReferences().toYAML()

        assertYamlEquals(
            actual = processedApiSpec,
            expected = """
            openapi: 3.0.3
            info:
              title: Check Health
              version: 1.0.0
            paths:
              /check:
                get:
                  summary: Check Health
                  responses:
                    '200':
                      description: successful operation
                      content:
                        application/json:
                          schema:
                            ${"$"}ref: ${tempDir.resolve("components/schemas/Health.yaml").canonicalPath}
                        text/plain:
                          schema:
                            ${"$"}ref: http://example.com/components/schemas/Health.yaml
            """.trimIndent()
        )
    }

    @Test
    fun `should not modify if referenced paths are http references`() {
        val openApiContent = """
        openapi: 3.0.3
        info:
          title: Check Health
          version: 1.0.0
        paths:
          /check:
            ${"$"}ref: http://example.com/paths/check.yaml
        """.trimIndent()

        val preProcessor = OpenApiPreProcessor(openApiContent, currentFile.canonicalPath)
        val processedOpenApiYamlContent = preProcessor.inlinePathReferences().toYAML()

        assertYamlEquals(openApiContent, processedOpenApiYamlContent)
    }

    private fun convertYamlToJsonRecursively(file: File, deleteOnConversion: Boolean = true) {
        file.walkTopDown().filter { it.isFile && (it.extension == "yaml" || it.extension == "yml") }.forEach { yamlFile ->
            val rawText = yamlFile.readText().replace(".yaml", ".json").replace(".yml", ".json")
            val data = yamlMapper.readTree(rawText)
            val jsonFile = File(yamlFile.parentFile, "${yamlFile.nameWithoutExtension}.json")
            mapper.writeValue(jsonFile, data)
            if (deleteOnConversion) yamlFile.delete()
        }
    }
}