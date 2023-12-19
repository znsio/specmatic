package `in`.specmatic.conversions

import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiSpecificationInfoTest {
    @Test
    fun `should return correct string representation for a specification with a single path and operation`() {
        // Arrange
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
        val openApiSpecificationInfo = OpenApiSpecificationInfo("testFilePath", openApi)

        // Act
        val actual = openApiSpecificationInfo.toString()

        // Assert
        val expected = """
          API Specification Summary: testFilePath
            OpenAPI Version: 3.0.0
            API Paths: 1, API Operations: 1
            Schema components: 1, Security Schemes: null

        """.trimIndent()

        assertThat(actual).isEqualTo(expected)
    }
}