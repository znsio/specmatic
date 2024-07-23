package integration_tests

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.specmatic.conversions.EnvironmentAndPropertiesConfiguration
import io.specmatic.conversions.EnvironmentAndPropertiesConfiguration.Companion.VALIDATE_RESPONSE_VALUE
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.Flags
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.value.Value
import io.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ValueAssertionsTest {

    @BeforeEach
    fun setup() {
        unmockkAll()
    }

    @Test
    fun `should validate exact header and body values in the response`() {
        val feature = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.3
info:
  title: My service
  description: My service
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product:
    post:
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required:
                - name
                - price
              properties:
                name:
                  type: string
                price:
                  type: number
            examples:
              NEW_PRODUCT:
                value:
                  name: "new product"
                  price: 100
      responses:
        200:
          description: Operation status
          headers:
            Header1:
              schema:
                type: string
              examples:
                NEW_PRODUCT:
                  value: "Header 1 value"
          content:
            text/plain:
              schema:
                type: string
              examples:
                NEW_PRODUCT:
                  value: "Product added successfully"
            """.trimIndent(),
            "",
            environmentAndPropertiesConfiguration = EnvironmentAndPropertiesConfiguration.setProperty(VALIDATE_RESPONSE_VALUE, "true")
        ).toFeature()
        feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(200, headers = mapOf("Header1" to "Header 1 value"), body = "Product added successfully")
            }

            override fun setServerState(serverState: Map<String, Value>) {

            }
        }).let { results ->
            assertThat(results.success()).withFailMessage(results.report()).isTrue()
        }

        feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(200, "Done")
            }

            override fun setServerState(serverState: Map<String, Value>) {

            }
        }).let { results ->
            assertThat(results.success()).withFailMessage(results.report()).isFalse()
        }
    }

    @Test
    fun `should validate exact header values in the response`() {
        val feature = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.3
info:
  title: My service
  description: My service
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product:
    post:
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required:
                - name
                - price
              properties:
                name:
                  type: string
                price:
                  type: number
            examples:
              NEW_PRODUCT:
                value:
                  name: "new product"
                  price: 100
      responses:
        200:
          description: Operation status
          headers:
            Header1:
              schema:
                type: integer
              examples:
                NEW_PRODUCT:
                  value: "Header 1 value"
            """.trimIndent(),
            "",
            environmentAndPropertiesConfiguration = EnvironmentAndPropertiesConfiguration(
                emptyMap(),
                mapOf(VALIDATE_RESPONSE_VALUE to "true")
            )
        ).toFeature()
        feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(200, headers = mapOf("Header1" to "10"))
            }
        }).let { results ->
            assertThat(results.success()).withFailMessage(results.report()).isTrue()
        }
    }

    @Test
    fun `validation should fail if there are extra response headers`() {
        val feature = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.3
info:
  title: My service
  description: My service
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product:
    post:
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required:
                - name
                - price
              properties:
                name:
                  type: string
                price:
                  type: number
            examples:
              NEW_PRODUCT:
                value:
                  name: "new product"
                  price: 100
      responses:
        200:
          description: Operation status
          headers:
            Header1:
              schema:
                type: integer
              examples:
                NEW_PRODUCT:
                  value: "Header 1 value"
            """.trimIndent(),
            "",
            environmentAndPropertiesConfiguration = EnvironmentAndPropertiesConfiguration(
                emptyMap(),
                mapOf(VALIDATE_RESPONSE_VALUE to "true")
            )
        ).toFeature()
            feature.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    return HttpResponse(200, headers = mapOf("Header1" to "10"))
                }
            }).let { results ->
                assertThat(results.success()).withFailMessage(results.report()).isTrue()
            }

        feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(200, headers = mapOf("Header1" to "not-a-number"))
            }
        }).let { results ->
            assertThat(results.success()).withFailMessage(results.report()).isFalse()
        }
    }

    @Test
    fun `should validate exact body value in the response`() {
        val specmaticConfig = mockk<SpecmaticConfig>(relaxed = true) {
            every { enableResponseValueValidation } returns true
        }

        val feature = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.3
info:
  title: My service
  description: My service
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product:
    post:
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required:
                - name
                - price
              properties:
                name:
                  type: string
                price:
                  type: number
            examples:
              NEW_PRODUCT:
                value:
                  name: "new product"
                  price: 100
      responses:
        200:
          description: Operation status
          content:
            text/plain:
              schema:
                type: string
              examples:
                NEW_PRODUCT:
                  value: "Product added successfully"
            """.trimIndent(),
            "",
            environmentAndPropertiesConfiguration = EnvironmentAndPropertiesConfiguration(
                specmaticConfig
            )
        ).toFeature()
        feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(200, body = "Product added successfully")
            }

            override fun setServerState(serverState: Map<String, Value>) {

            }
        }).let { results ->
            assertThat(results.success()).withFailMessage(results.report()).isTrue()
        }

        feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(200, "Done")
            }

            override fun setServerState(serverState: Map<String, Value>) {

            }
        }).let { results ->
            assertThat(results.success()).withFailMessage(results.report()).isFalse()
        }
    }

    @Test
    fun `breadcrumb for response value validation failure should not duplicate RESPONSE`() {
        val feature = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.3
info:
  title: My service
  description: My service
  version: 1.0.0
servers:
  - url: 'https://localhost:8080'
paths:
  /product:
    post:
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required:
                - name
                - price
              properties:
                name:
                  type: string
                price:
                  type: number
            examples:
              NEW_PRODUCT:
                value:
                  name: "new product"
                  price: 100
      responses:
        200:
          description: Operation status
          headers:
            Header1:
              schema:
                type: string
              examples:
                NEW_PRODUCT:
                  value: "Header 1 value"
          content:
            text/plain:
              schema:
                type: string
              examples:
                NEW_PRODUCT:
                  value: "Product added successfully"
            """.trimIndent(),
            "",
            environmentAndPropertiesConfiguration = EnvironmentAndPropertiesConfiguration(
                emptyMap(),
                mapOf(VALIDATE_RESPONSE_VALUE to "true")
            )
        ).toFeature()
        feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(200, "Product added successfully")
            }
        }).let { results ->
            assertThat(results.report()).contains(">> RESPONSE.HEADERS.Header1")
        }
    }
}