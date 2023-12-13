package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.pattern.parsedJSONObject
import `in`.specmatic.stub.HttpStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExamplesAsStub {
    @Test
    fun `any value should match the given security scheme`() {
        val feature = OpenApiSpecification.fromYAML("""
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
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required:
                - message
              properties:
                message:
                  type: string
            examples:
              SUCCESS:
                value:
                  message: Hello World!
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
              examples:
                SUCCESS:
                  value:
                    Hello to you!
components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer

security:
  - BearerAuth: []
         """.trimIndent(), "").toFeature()

        HttpStub(feature).use {
            val response = it.client.execute(HttpRequest(
                "POST",
                "/hello",
                mapOf("Authorization" to "Bearer 1234"),
                parsedJSONObject("""{"message": "Hello World!"}""")
            ))

            assertThat(response.body.toStringLiteral()).isEqualTo("Hello to you!")
        }
    }
}