package `in`.specmatic.conversions

import `in`.specmatic.core.Flags
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AssertionsTest {
    @Test
    fun `temp`() {
        try {
            System.setProperty(Flags.VALIDATE_RESPONSE, "true")

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
            """.trimIndent(), ""
            ).toFeature()
            feature.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    return HttpResponse(200, "Product added successfully")
                }

                override fun setServerState(serverState: Map<String, Value>) {

                }
            }).let { results ->
                assertThat(results.success()).isTrue()
            }

            feature.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    return HttpResponse(200, "Done")
                }

                override fun setServerState(serverState: Map<String, Value>) {

                }
            }).let { results ->
                assertThat(results.success()).isFalse()
            }
        } finally {
            System.clearProperty("VALIDATE_BODY")
        }
    }
}