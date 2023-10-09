package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedJSONObject
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RefPartParsingTests {
    @Test
    fun `path parameter ref`() {
        val specification = """
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
  /hello/{id}:
    get:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      parameters:
        - ${"$"}ref: '#/components/parameters/Id'
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
              examples:
                200_OK:
                  value: success
components:
  parameters:
    Id:
      in: path
      name: id
      schema:
        type: integer
      examples:
        200_OK:
          value: 10
      required: true
      description: Numeric ID
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.path).isEqualTo("/hello/10")
                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.success()).isTrue()
        assertThat(results.successCount).isEqualTo(1)
    }

    @Test
    fun `path example ref`() {
        val specification = """
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
  /hello/{id}:
    get:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      parameters:
        - ${"$"}ref: '#/components/parameters/Id'
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
              examples:
                200_OK:
                  value: success
components:
  parameters:
    Id:
      in: path
      name: id
      schema:
        type: integer
      examples:
        200_OK:
          ${"$"}ref: '#/components/examples/Id_Request_Example'
      required: true
      description: Numeric ID
  examples:
    Id_Request_Example:
      value: 10
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.path).isEqualTo("/hello/10")
                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.success()).isTrue()
        assertThat(results.successCount).isEqualTo(1)
    }

    @Test
    fun `request header ref`() {
        val specification = """
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
        - ${"$"}ref: '#/components/parameters/HelloRequestHeader'
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
              examples:
                200_OK:
                  value: success
components:
  parameters:
    HelloRequestHeader:
      in: header
      name: X-HelloResponseHeader
      schema:
        type: string
      examples:
        200_OK:
          value: helloworld
      required: true
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.path).isEqualTo("/hello")
                assertThat(request.headers["X-HelloResponseHeader"]).isEqualTo("helloworld")

                return HttpResponse.OK("success").copy(headers = mapOf("X-HelloResponseHeader" to "world"))
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        println(results.report())

        assertThat(results.success()).isTrue()
        assertThat(results.successCount).isEqualTo(1)
    }

    @Test
    fun `request header example ref`() {
        val specification = """
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
        - ${"$"}ref: '#/components/parameters/HelloRequestHeader'
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
              examples:
                200_OK:
                  value: success
components:
  parameters:
    HelloRequestHeader:
      in: header
      name: X-HelloResponseHeader
      schema:
        type: string
      examples:
        200_OK:
          ${"$"}ref: '#/components/examples/Request_Header_Example'
      required: true
  examples:
    Request_Header_Example:
      value: helloworld
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.path).isEqualTo("/hello")
                assertThat(request.headers["X-HelloResponseHeader"]).isEqualTo("helloworld")

                return HttpResponse.OK("success").copy(headers = mapOf("X-HelloResponseHeader" to "world"))
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        println(results.report())

        assertThat(results.success()).isTrue()
        assertThat(results.successCount).isEqualTo(1)
    }

    @Test
    fun `request header ref where header component is missing`() {
        val specification = """
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
      responses:
        '200':
          description: Says hello
          headers:
            X-HelloResponseHeader:
              ${"$"}ref: '#/components/headers/HelloResponseHeader'
          content:
            application/json:
              schema:
                type: string
              examples:
                200_OK:
                  value: success
        """.trimIndent()

        assertThatThrownBy {
            OpenApiSpecification.fromYAML(specification, "").toFeature()
        }
            .isInstanceOf(ContractException::class.java)
            .hasMessageContaining("X-HelloResponseHeader")
    }

    @Test
    fun `query param ref`() {
        val specification = """
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
        - ${"$"}ref: '#/components/parameters/Id'
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
              examples:
                200_OK:
                  value: success
components:
  parameters:
    Id:
      in: query
      name: id
      schema:
        type: integer
      examples:
        200_OK:
          value: 10
      required: true
      description: Numeric ID
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.path).isEqualTo("/hello")
                assertThat(request.queryParams["id"]).isEqualTo("10")

                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.success()).isTrue()
        assertThat(results.successCount).isEqualTo(1)
    }

    @Test
    fun `query param example ref`() {
        val specification = """
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
        - ${"$"}ref: '#/components/parameters/Id'
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
              examples:
                200_OK:
                  value: success
components:
  parameters:
    Id:
      in: query
      name: id
      schema:
        type: integer
      examples:
        200_OK:
          ${"$"}ref: '#/components/examples/Query_Parameter_Example'
      required: true
      description: Numeric ID
  examples:
    Query_Parameter_Example:
      value: 10
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.path).isEqualTo("/hello")
                assertThat(request.queryParams["id"]).isEqualTo("10")

                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.success()).isTrue()
        assertThat(results.successCount).isEqualTo(1)
    }

    @Test
    fun `request body ref`() {
        val specification = """
---
openapi: "3.0.1"
info:
  title: "Person API"
  version: "1"
paths:
  /person:
    post:
      summary: "Get person by id"
      parameters: []
      requestBody:
        ${"$"}ref: '#/components/requestBodies/PersonRequest'
      responses:
        200:
          description: "Get person by id"
          content:
            text/plain:
              schema:
                type: "string"
              examples:
                200_OK:
                  value: "success"
components:
  requestBodies:
    PersonRequest:
      content:
        application/json:
          schema:
            required:
            - "id"
            properties:
              id:
                type: "string"
          examples:
            200_OK:
              value:
                id: "abc123"
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val expectedRequest = parsedJSONObject("""{"id":"abc123"}""")
                assertThat(request.body).isEqualTo(expectedRequest)
                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        println(results.distinctReport())

        assertThat(results.failureCount).isEqualTo(0)
        assertThat(results.successCount).isEqualTo(1)
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `request body with nested allOf refs`() {
        val specification = """
---
openapi: "3.0.1"
info:
  title: "Person API"
  version: "1"
paths:
  /person:
    post:
      summary: "Get person by id"
      parameters: []
      requestBody:
        ${"$"}ref: '#/components/requestBodies/PersonRequest'
      responses:
        200:
          description: "Get person by id"
          content:
            text/plain:
              schema:
                type: "string"
              examples:
                200_OK:
                  value: "success"
components:
  schemas:
    Id:
      type: object
      properties:
        id:
          type: string
      required:
        - id
    OtherData:
      allOf:
        - type: object
          properties:
            name:
              type: string
          required:
            - name
        - ${"$"}ref: '#/components/schemas/Description'
    Description:
      type: object
      properties:
        description:
          type: string
      required:
          - description
  requestBodies:
    PersonRequest:
      content:
        application/json:
          schema:
            allOf:
              - ${"$"}ref: '#/components/schemas/Id'
              - ${"$"}ref: '#/components/schemas/OtherData'
          examples:
            200_OK:
              value:
                id: "abc123"
                name: "Jane"
                description: "A person"
        """.trimIndent()

        val apiSpecification = OpenApiSpecification.fromYAML(specification, "")
        val feature = apiSpecification.toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val expectedRequest = parsedJSONObject("""{"id":"abc123", "name": "Jane", "description": "A person"}""")
                assertThat(request.body).isEqualTo(expectedRequest)
                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        println(results.distinctReport())

        assertThat(results.failureCount).isEqualTo(0)
        assertThat(results.successCount).isEqualTo(1)
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `response body ref`() {
        val specification = """
---
openapi: "3.0.1"
info:
  title: "Person API"
  version: "1"
paths:
  /person:
    post:
      summary: "Get person by id"
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              required:
              - "name"
              properties:
                name:
                  type: "string"
            examples:
              200_OK:
                value:
                  name: "Jack"
      responses:
        200:
          ${"$"}ref: '#/components/responses/IdResponse'
components:
  responses:
    IdResponse:
      description: "Get person by id"
      content:
        application/json:
          schema:
            required:
            - "id"
            properties:
              id:
                type: integer
          examples:
            200_OK:
              value:
                name: 123
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val expectedRequest = parsedJSONObject("""{"name":"Jack"}""")
                assertThat(request.body).isEqualTo(expectedRequest)
                return HttpResponse.OK(parsedJSONObject("""{"id":123}"""))
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        println(results.distinctReport())

        assertThat(results.failureCount).isEqualTo(0)
        assertThat(results.successCount).isEqualTo(1)
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `ref within allOf`() {
        val specification = """
---
openapi: "3.0.1"
info:
  title: "Person API"
  version: "1"
paths:
  /person:
    post:
      summary: "Get person by id"
      parameters: []
      requestBody:
        ${"$"}ref: '#/components/requestBodies/PersonRequest'
      responses:
        200:
          description: "Get person by id"
          content:
            text/plain:
              schema:
                type: "string"
              examples:
                200_OK:
                  value: "success"
components:
  schemas:
    Id:
      type: object
      properties:
        id:
          type: string
      required:
        - id
    Name:
      type: object
      properties:
        name:
          type: string
      required:
        - name
  requestBodies:
    PersonRequest:
      content:
        application/json:
          schema:
            allOf:
              - ${"$"}ref: '#/components/schemas/Id'
              - ${"$"}ref: '#/components/schemas/Name'
          examples:
            200_OK:
              value:
                id: "abc123"
                name: "Jane"
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val expectedRequest = parsedJSONObject("""{"id": "abc123", "name":"Jane"}""")
                assertThat(request.body).isEqualTo(expectedRequest)
                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        println(results.distinctReport())

        assertThat(results.failureCount).isEqualTo(0)
        assertThat(results.successCount).isEqualTo(1)
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `request body example ref`() {
        val specification = """
---
openapi: "3.0.1"
info:
  title: "Person API"
  version: "1"
paths:
  /person:
    post:
      summary: "Get person by id"
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              required:
              - "name"
              properties:
                name:
                  type: "string"
            examples:
              200_OK:
                ${"$"}ref: '#/components/examples/200_OK_Request_Example' 
      responses:
        200:
          description: "Get person by id"
          content:
            application/json:
              schema:
                required:
                - "id"
                properties:
                  id:
                    type: integer
              examples:
                200_OK:
                  value:
                    name: 123
components:
  examples:
    200_OK_Request_Example:
      value:
        name: "Jack"

        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val expectedRequest = parsedJSONObject("""{"name":"Jack"}""")
                assertThat(request.body).isEqualTo(expectedRequest)
                return HttpResponse.OK(parsedJSONObject("""{"id":123}"""))
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        println(results.distinctReport())

        assertThat(results.failureCount).isEqualTo(0)
        assertThat(results.successCount).isEqualTo(1)
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `response body example ref`() {
        val specification = """
---
openapi: "3.0.1"
info:
  title: "Person API"
  version: "1"
paths:
  /person:
    post:
      summary: "Get person by id"
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              required:
              - "name"
              properties:
                name:
                  type: "string"
            examples:
              200_OK:
                value:
                  name: "Jack"
      responses:
        200:
          description: "Get person by id"
          content:
            application/json:
              schema:
                required:
                - "id"
                properties:
                  id:
                    type: integer
              examples:
                200_OK:
                  value:
                    ${"$"}ref: '#/components/examples/200_OK_Response_Example'
components:
  examples:
    200_OK_Response_Example:
      value:
        name: "123"

        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val expectedRequest = parsedJSONObject("""{"name":"Jack"}""")
                assertThat(request.body).isEqualTo(expectedRequest)
                return HttpResponse.OK(parsedJSONObject("""{"id":123}"""))
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        println(results.distinctReport())

        assertThat(results.failureCount).isEqualTo(0)
        assertThat(results.successCount).isEqualTo(1)
        assertThat(results.success()).isTrue()
    }
}
