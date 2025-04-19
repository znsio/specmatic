package io.specmatic.conversions

import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.parser.OpenAPIV3Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
    fun `should instantiate feature with empty servers list if no servers are defines in oas`() {
        val openApiSpec = """
        openapi: 3.0.3
        info:
          title: Hello API
          version: 1.0.0
        paths:
          /hello:
            get:
              responses:
                '200':
                  description: A successful response
        """.trimIndent()
        val openApiSpecification = OpenApiSpecification.fromYAML(openApiSpec, "")
        val feature = openApiSpecification.toFeature()

        assertThat(feature.servers).isEmpty()
    }

    @Test
    fun `should extract servers from oas servers list into feature when not empty`() {
        val openApiSpec = """
        openapi: 3.0.3
        info:
          title: Hello API
          version: 1.0.0
        servers:
          - url: http://localhost:8080
          - url: http://www.example.com/api
        paths:
          /hello:
            get:
              responses:
                '200':
                  description: A successful response
        """.trimIndent()
        val openApiSpecification = OpenApiSpecification.fromYAML(openApiSpec, "")
        val feature = openApiSpecification.toFeature()

        assertThat(feature.servers).containsExactly(
            "http://localhost:8080",
            "http://www.example.com/api"
        )
    }

    @Test
    fun `should fail when swagger parser stops adding default server to servers list if its empty or null in oas`() {
        val openApiSpec = """
        openapi: 3.0.3
        info:
          title: Hello API
          version: 1.0.0
        paths:
          /hello:
            get:
              responses:
                '200':
                  description: A successful response
        """.trimIndent()
        val openApi = OpenAPIV3Parser().readContents(openApiSpec)

        assertThat(openApi.openAPI.servers).containsExactly(
            Server().apply { url = "/" }
        )
    }
}