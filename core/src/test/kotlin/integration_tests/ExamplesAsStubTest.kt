package integration_tests

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.pattern.parsedJSONArray
import `in`.specmatic.core.pattern.parsedJSONObject
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.stub.captureStandardOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class ExamplesAsStubTest {
    private val featureWithQueryParamExamples = OpenApiSpecification.fromYAML(
        """
openapi: 3.0.1
info:
  title: Data API
  version: "1"
paths:
  /:
    get:
      summary: Data
      parameters:
        - name: type
          schema:
            type: string
          in: query
          required: true
          examples:
            QUERY_SUCCESS:
              value: data
      responses:
        "200":
          description: Data
          content:
            application/json:
              schema:
                type: array
                items:
                  type:
                    string
              examples:
                QUERY_SUCCESS:
                  value: ["one", "two"]
""".trim(), ""
    ).toFeature()

    private val featureWithPathParamExamples = OpenApiSpecification.fromYAML(
        """
openapi: 3.0.1
info:
  title: Data API
  version: "1"
paths:
  /{productId}:
    get:
      summary: Data
      parameters:
        - name: productId
          schema:
            type: string
          in: path
          required: true
          examples:
            PATH_SUCCESS:
              value: xyz123
            PATH_FAILURE:
              value: abc123
      responses:
        "200":
          description: Data
          content:
            application/json:
              schema:
                type: array
                items:
                  type:
                    string
              examples:
                PATH_SUCCESS:
                  value: ["one", "two"]
        "404":
          description: Data not found
          content:
            text/plain:
              schema:
                type: string
              examples:
                PATH_FAILURE:
                  value: "Could not find abc123"
""".trim(), ""
    ).toFeature()

    private val featureWithHeaderParamExamples = OpenApiSpecification.fromYAML(
        """
openapi: 3.0.1
info:
  title: Header Example API
  version: "1"
paths:
  /hello:
    get:
      parameters:
        - name: userId
          schema:
            type: string
          in: header
          required: true
          examples:
            HEADER_SUCCESS:
              value: John
            HEADER_FAILURE:
              value: Jane
      responses:
        "200":
          description: Data
          content:
            text/plain:
              schema:
                type: string
              examples:
                HEADER_SUCCESS:
                  value: "Hello John"
        "401":
          description: Unauthorized
          content:
            text/plain:
              schema:
                type: string
              examples:
                HEADER_FAILURE:
                  value: "User Jane not authorized"
""".trim(), ""
    ).toFeature()

    private val featureWithOmittedParamExamples = OpenApiSpecification.fromYAML(
        """
openapi: 3.0.1
info:
  title: Header Example API
  version: "1"
paths:
  /hello:
    get:
      parameters:
        - name: userId
          schema:
            type: string
          in: header
          required: true
          examples:
            HEADER_SUCCESS:
              value: John
        - name: role
          schema:
            type: string
          in: header
          required: false
          examples:
            HEADER_SUCCESS:
              value: "(omit)"
      responses:
        "200":
          description: Data
          content:
            text/plain:
              schema:
                type: string
              examples:
                HEADER_SUCCESS:
                  value: "Hello John"
""".trim(), ""
    ).toFeature()

    @Test
    fun `any value should match the given security scheme`() {
        val feature = OpenApiSpecification.fromYAML(
            """
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
         """.trimIndent(), ""
        ).toFeature()

        val credentials = "Basic " + Base64.getEncoder().encodeToString("user:password".toByteArray())

        HttpStub(feature).use {
            val response = it.client.execute(HttpRequest(
                "POST",
                "/hello",
                mapOf("Authorization" to "Bearer $credentials"),
                parsedJSONObject("""{"message": "Hello World!"}""")
            ))

            assertThat(response.body.toStringLiteral()).isEqualTo("Hello to you!")
        }
    }

    @Test
    fun `examples as stub should work for request with body when there are no security schemes`() {
        val feature = OpenApiSpecification.fromYAML(
            """
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
         """.trimIndent(), ""
        ).toFeature()

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
        val feature = OpenApiSpecification.fromYAML(
            """
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
         """.trimIndent(), ""
        ).toFeature()

        val credentials = "Basic " + Base64.getEncoder().encodeToString("user:password".toByteArray())

        HttpStub(feature).use {
            val response = it.client.execute(HttpRequest(
                "GET",
                "/hello",
                mapOf("Authorization" to "Bearer $credentials"),
                queryParametersMap = mapOf("greeting" to "Hello")
            ))

            assertThat(response.body.toStringLiteral()).isEqualTo("Hello to you!")
        }
    }

    @Test
    fun `expectations for query params from examples`() {
        HttpStub(featureWithQueryParamExamples).use { stub ->
            stub.client.execute(HttpRequest("GET", "/?type=data"))
                .let { response ->
                    assertThat(response.status).isEqualTo(200)
                    assertThat(response.body).isEqualTo(parsedJSONArray("""["one", "two"]"""))
                }
        }
    }

    @Test
    fun `expectations for path params from examples`() {
        HttpStub(featureWithPathParamExamples).use { stub ->
            stub.client.execute(HttpRequest("GET", "/xyz123"))
                .let { response ->
                    assertThat(response.status).isEqualTo(200)
                    assertThat(response.body).isEqualTo(parsedJSONArray("""["one", "two"]"""))
                }
            stub.client.execute(HttpRequest("GET", "/abc123"))
                .let { response ->
                    assertThat(response.status).isEqualTo(404)
                    assertThat(response.body.toStringLiteral()).isEqualTo("Could not find abc123")
                }
        }
    }

    @Test
    fun `expectations for header params from examples`() {
        HttpStub(featureWithHeaderParamExamples).use { stub ->
            stub.client.execute(HttpRequest("GET", "/hello", headers = mapOf("userId" to "John")))
                .let { response ->
                    assertThat(response.status).isEqualTo(200)
                    assertThat(response.body.toStringLiteral()).isEqualTo("Hello John")
                }
            stub.client.execute(HttpRequest("GET", "/hello", headers = mapOf("userId" to "Jane")))
                .let { response ->
                    assertThat(response.status).isEqualTo(401)
                    assertThat(response.body.toStringLiteral()).isEqualTo("User Jane not authorized")
                }
        }
    }

    @Test
    fun `expectations from examples ignores omitted parameters while matching stub request`() {
        HttpStub(featureWithOmittedParamExamples).use { stub ->
            stub.client.execute(HttpRequest("GET", "/hello", headers = mapOf("userId" to "John")))
                .let { response ->
                    assertThat(response.status).isEqualTo(200)
                    assertThat(response.body.toStringLiteral()).isEqualTo("Hello John")
                }
        }
    }

    @Test
    fun `expectations from inline example in response headers`() {
        val spec = """
openapi: 3.0.0
info:
  title: Product API
  version: 0.1.9
paths:
  /products:
    post:
      summary: create product
      description: create product
      requestBody:
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
              REQUEST_BODY_REF_200:
                value:
                  name: 'Macbook'
      responses:
        '200':
          description: OK
          headers:
            X-RateLimit-Limit:
              schema:
                type: integer
              description: Request limit per hour.
              examples:
                REQUEST_BODY_REF_200:
                  value:
                    100
          content:
            application/json:
              schema:
                ${'$'}ref: '#/components/schemas/ProductId'
              examples:
                REQUEST_BODY_REF_200:
                  value:
                    id: 10
components:
  schemas:
    ProductId:
      title: Product Id
      type: object
      properties:
        id:
          type: integer
      required:
        - id
  examples:
    NAME_200:
      value:
        name: 'Macbook'""".trimIndent()

        val contract = OpenApiSpecification.fromYAML(spec, "").toFeature()
        HttpStub(contract).use { stub ->
            stub.client.execute(HttpRequest("POST", "/products", emptyMap(), parsedJSONObject("""{"name": "Macbook"}""")))
                .let { response ->
                    assertThat(response.status).isEqualTo(200)
                    assertThat(response.headers["X-RateLimit-Limit"]).isEqualTo("100")
                    assertThat(response.body).isEqualTo(
                        parsedJSONObject("""
                        {
                            "id": 10
                        }
                    """.trimIndent())
                    )
                }
        }
    }

    @Test
    fun `expectations with path param + header + request body`() {
        val spec = """
openapi: 3.0.0
info:
  title: Product API
  version: 0.1.9
paths:
  /products/{id}:
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: integer
        examples:
          SUCCESS:
            value: 10
      - name: trace-id
        in: header
        required: true
        schema:
          type: integer
        examples:
          SUCCESS:
            value: 10
    post:
      summary: create product
      description: create product
      requestBody:
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
              SUCCESS:
                value:
                  name: 'Macbook'
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: string
              examples:
                SUCCESS:
                  value: success
""".trimIndent()

        val contract = OpenApiSpecification.fromYAML(spec, "").toFeature()
        HttpStub(contract).use { stub ->
            val request = HttpRequest(
                "POST",
                "/products/10",
                mapOf("trace-id" to "10"),
                parsedJSONObject("""{"name": "Macbook"}""")
            )
            val response = stub.client.execute(request)
            assertThat(response.status).isEqualTo(200)
            assertThat(response.body.toStringLiteral()).isEqualTo("success")
        }
    }

    @Test
    fun `errors when loading examples with invalid values as expectations should mention the example name`() {
        val nameOfExample = "EXAMPLE_OF_SUCCESS"
        val spec = """
openapi: 3.0.0
info:
  title: Product API
  version: 0.1.9
paths:
  /products/{id}:
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: integer
        examples:
          $nameOfExample:
            value: "abc"
    post:
      summary: create product
      description: create product
      requestBody:
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
              $nameOfExample:
                value:
                  name: 'Macbook'
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: string
              examples:
                $nameOfExample:
                  value: success
""".trimIndent()

        val contract = OpenApiSpecification.fromYAML(spec, "").toFeature()
        val (output, _) = captureStandardOutput {
            try {
                HttpStub(contract).close()
            } catch(_: Throwable) {
            }
        }

        assertThat(output).withFailMessage(output).contains("EXAMPLE_OF_SUCCESS")
    }
}
