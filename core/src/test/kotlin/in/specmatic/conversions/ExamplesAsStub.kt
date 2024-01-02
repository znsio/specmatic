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

    @Test
    fun `examples as stub should work for request with body when there are no security schemes`() {
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
         """.trimIndent(), "").toFeature()

        HttpStub(feature).use {
            val response = it.client.execute(HttpRequest(
                "POST",
                "/hello",
                body = parsedJSONObject("""{"message": "Hello World!"}""")
            ))

            assertThat(response.body.toStringLiteral()).isEqualTo("Hello to you!")
        }
    }

    @Test
    fun `any value should match the given security scheme when request body does not exist`() {
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
    get:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      parameters:
        - name: greeting
          schema:
            type: string
          in: query
          examples:
            SUCCESS:
              value: "Hello"
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
                "GET",
                "/hello",
                mapOf("Authorization" to "Bearer 1234"),
                queryParams = mapOf("greeting" to "Hello")
            ))

            assertThat(response.body.toStringLiteral()).isEqualTo("Hello to you!")
        }
    }
}