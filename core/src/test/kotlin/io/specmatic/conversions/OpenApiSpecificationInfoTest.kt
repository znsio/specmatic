package io.specmatic.conversions

import io.specmatic.core.value.BooleanValue
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.security.SecurityScheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OpenApiSpecificationInfoTest {
    @Test
    fun `should generate summary with a single path and operation`() {
        val openApi = OpenAPI().apply {
            openapi = "3.0.0"
            info = Info().apply {
                title = "Test API"
                version = "1.0.0"
                description = "This is a test API"
            }
            paths = Paths().also { paths ->
                paths.addPathItem("/test", PathItem().also { pathItem ->
                    pathItem.get = Operation().also { operation -> operation.operationId = "testOperation" }
                })
            }
            components = Components().also {
                it.schemas = mutableMapOf("testSchema" to Schema<Any>())
            }
        }

        val expected = """
          API Specification Summary: testFilePath
            OpenAPI Version: 3.0.0
            API Paths: 1, API Operations: 1
            Schema components: 1, Security Schemes: none

        """.trimIndent()

        assertThat(openApiSpecificationInfo("testFilePath", openApi)).isEqualTo(expected)
    }

    @Test
    fun `should generate summary with multiple paths and operations`() {
        val openApi = OpenAPI().apply {
            openapi = "3.0.0"
            info = Info().apply {
                title = "Test API"
                version = "1.0.0"
                description = "This is a test API"
            }
            paths = Paths().also { paths ->
                paths.addPathItem("/test1", PathItem().also { pathItem ->
                    pathItem.get = Operation().also { operation -> operation.operationId = "testOperation1" }
                })
                paths.addPathItem("/test2", PathItem().also { pathItem ->
                    pathItem.post = Operation().also { operation -> operation.operationId = "testOperation2" }
                })
            }
            components = Components().also { components ->
                components.schemas = mutableMapOf("testSchema1" to Schema<Any>(), "testSchema2" to Schema<Any>())
                components.securitySchemes =
                    mutableMapOf("testSecurityScheme" to SecurityScheme().also { it.type = SecurityScheme.Type.APIKEY })
            }
        }

        val expected = """
          API Specification Summary: testFilePath
            OpenAPI Version: 3.0.0
            API Paths: 2, API Operations: 2
            Schema components: 2, Security Schemes: [apiKey]

        """.trimIndent()

        assertThat(openApiSpecificationInfo("testFilePath", openApi)).isEqualTo(expected)
    }

    @Test
    fun `should generate summary with no paths and operations`() {
        val openApi = OpenAPI().apply {
            openapi = "3.0.0"
            info = Info().apply {
                title = "Test API"
                version = "1.0.0"
                description = "This is a test API"
            }
            paths = Paths()
            components = Components()
        }

        val expected = """
          API Specification Summary: testFilePath
            OpenAPI Version: 3.0.0
            API Paths: 0, API Operations: 0
            Schema components: null, Security Schemes: none

        """.trimIndent()

        assertThat(openApiSpecificationInfo("testFilePath", openApi)).isEqualTo(expected)
    }

    @Test
    fun `should be able to load dictionary in yaml format`(@TempDir tempDir: File) {
        val apiFile = tempDir.resolve("api.yaml")
        val yamlDictionary = """
        Schema.stringKey: stringValue
        Schema.numberKey: 123
        Schema.booleanKey: true
        Schema.nullKey: null
        Schema.nested.key: value
        Schema.array[*]: value
        Schema.array[0].key: value
        """.trimIndent()
        tempDir.resolve("api_dictionary.yaml").writeText(yamlDictionary)
        val dictionary = OpenApiSpecification.loadDictionary(apiFile.canonicalPath, null)
        val expectedEntries = mapOf(
            "Schema.stringKey" to StringValue("stringValue"),
            "Schema.numberKey" to NumberValue(123),
            "Schema.booleanKey" to BooleanValue(true),
            "Schema.nullKey" to NullValue,
            "Schema.nested.key" to StringValue("value"),
            "Schema.array[*]" to StringValue("value"),
            "Schema.array[0].key" to StringValue("value"),
        ).entries

        assertThat(expectedEntries).allSatisfy { (key, value) ->
            assertThat(dictionary.containsKey(key))
            assertThat(dictionary.getRawValue(key)).isEqualTo(value)
        }
    }
}