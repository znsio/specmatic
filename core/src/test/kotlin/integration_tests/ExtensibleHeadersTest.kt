package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExtensibleHeadersTest {

    @Test
    fun `should assert that additional 'X-Custom-Header' (not in spec) is present in the request`() {
        val feature = OpenApiSpecification.fromFile("src/test/resources/openapi/specs_for_additional_headers_in_examples/additional_headers_test.yaml").toFeature().loadExternalisedExamples()

        var customHeaderValue: String? = null

        feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                println(request.toLogString())

                customHeaderValue = request.headers["X-Custom-Header"]

                return HttpResponse.OK
            }
        })

        assertNotNull(customHeaderValue, "'X-Custom-Header' must be present in the request.")
        assertEquals("Custom---Value", customHeaderValue, "Unexpected value for 'X-Custom-Header'.")

        println("'X-Custom-Header' is present and has the correct value.")
    }

    @Test
    fun `api with one example and api key auth with generative tests should generate only one positive test`() {
        val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
  version: 0.1.9
servers:
  - url: http://api.example.com/v1
    description: Optional server description, e.g. Main (production) server
  - url: http://staging-api.example.com
    description: Optional server description, e.g. Internal staging server for testing
paths:
  /hello:
    post:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      security:
        - ApiKeyAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - name
              properties:
                name:
                  type: string
            examples:
              example1:
                value:
                  name: Alice
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: object
                required:
                  - greeting
                properties:
                  greeting:
                    type: string
              examples:
                example1:
                  value:
                    greeting: Hi!
components:
  securitySchemes:
    ApiKeyAuth:
      type: apiKey
      in: header
      name: Authenticate
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature().enableGenerativeTesting(true)

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse.ok(parsedJSONObject("""{"greeting": "Hi!"}""")).also {
                    println(request.toLogString())
                    println(it.toLogString())
                }
            }
        })

        assertThat(results.testCount).isEqualTo(1)
    }
}
