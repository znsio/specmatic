package `in`.specmatic.conversions

import `in`.specmatic.core.HttpHeadersPattern
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.pattern.DeferredPattern
import `in`.specmatic.core.pattern.NullPattern
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.parsedJSON
import io.ktor.util.reflect.*
import io.swagger.util.Yaml
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class OpenApiSpecificationTest {
    companion object {
        const val OPENAPI_FILE = "openApiTest.yaml"
    }

    @BeforeEach
    fun `setup`() {
        val openAPI = """
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
        - in: path
          name: id
          schema:
            type: integer
          required: true
          description: Numeric ID
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
              examples:
                200_OKAY:
                  value: hello
                  summary: response example without any matching parameters with examples
        '404':
          description: Not Found
          content:
            application/json:
              schema:
                type: string
        '400':
          description: Bad Request
          content:
            application/json:
              schema:
                type: string
  /nested/types/without/ref/to/parent:
    get:
      summary: Nested Types
      description: Optional extended description in CommonMark or HTML.
      responses:
        '200':
          description: Returns nested type
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/NestedTypeWithoutRef'
  /nested/types/with/ref/to/parent:
    get:
      summary: Nested Types
      description: Optional extended description in CommonMark or HTML.
      responses:
        '200':
          description: Returns nested type
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/NestedTypeWithRef'

components:
  schemas:
    NestedTypeWithoutRef:
      description: ''
      type: object
      properties:
        Parent:
          type: object
          properties:
            Child:
              type: object
              properties:
                Parent:
                  type: object
                  properties:
                    Child:
                      type: string
    NestedTypeWithRef:
      description: ''
      type: object
      properties:
        Parent:
          type: object
          properties:
            Child:
              ${"$"}ref: '#/components/schemas/NestedTypeWithRef'
    """.trim()

        val openApiFile = File(OPENAPI_FILE)
        openApiFile.createNewFile()
        openApiFile.writeText(openAPI)

    }

    @AfterEach
    fun `teardown`() {
        File(OPENAPI_FILE).delete()
    }

    @Ignore
    fun `should generate 200 OK scenarioInfos from openAPI`() {
        val openApiSpecification = OpenApiSpecification.fromFile(OPENAPI_FILE)
        val scenarioInfos = openApiSpecification.toScenarioInfos()
        assertThat(scenarioInfos.size).isEqualTo(3)
    }

    @Test
    fun `should not resolve non ref nested types to Deferred Pattern`() {
        val openApiSpecification = OpenApiSpecification.fromFile(OPENAPI_FILE)
        val scenarioInfos = openApiSpecification.toScenarioInfos()
        val nestedTypeWithoutRef = scenarioInfos.first().patterns.getOrDefault("(NestedTypeWithoutRef)", NullPattern)
        assertThat(containsDeferredPattern(nestedTypeWithoutRef)).isFalse
    }

    @Test
    fun `should resolve ref nested types to Deferred Pattern`() {
        val openApiSpecification = OpenApiSpecification.fromFile(OPENAPI_FILE)
        val scenarioInfos = openApiSpecification.toScenarioInfos()
        val nestedTypeWithRef = scenarioInfos.first().patterns["(NestedTypeWithRef)"]
        assertThat(containsDeferredPattern(nestedTypeWithRef!!)).isTrue
    }

    private fun containsDeferredPattern(pattern: Pattern): Boolean {
        if (!pattern.pattern.instanceOf(Map::class)) return false
        val childPattern = (pattern.pattern as Map<String, Pattern?>).values.firstOrNull() ?: return false
        return if (childPattern.instanceOf(DeferredPattern::class)) true
        else containsDeferredPattern(childPattern)
    }

    @Test
    fun `none of the scenarios should expect the Content-Type header`() {
        val openApiSpecification = OpenApiSpecification.fromFile(OPENAPI_FILE)
        val scenarioInfos = openApiSpecification.toScenarioInfos()

        for (scenarioInfo in scenarioInfos) {
            assertNotFoundInHeaders("Content-Type", scenarioInfo.httpRequestPattern.headersPattern)
            assertNotFoundInHeaders("Content-Type", scenarioInfo.httpResponsePattern.headersPattern)
        }
    }

    @Test
    fun `programmatically construct OpenAPI YAML for GET with request headers and path and query params`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Products API

Scenario: Get product by id
  When GET /products/(id:number)/variants/(variantId:number)?tag=(string)
  And request-header Authentication (string)
  And request-header OptionalHeader? (string)
  Then status 200
  And response-body (string)
""".trim()
        )
        val openAPI = feature.toOpenApi()
        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        assertThat(openAPIYaml).isEqualTo(
            """---
openapi: "3.0.1"
info:
  title: "Products API"
  version: "1"
paths:
  /products/{id}/variants/{variantId}:
    get:
      parameters:
      - name: "id"
        in: "path"
        required: true
        schema:
          type: "number"
      - name: "variantId"
        in: "path"
        required: true
        schema:
          type: "number"
      - name: "tag"
        in: "query"
        schema:
          type: "string"
      - name: "Authentication"
        in: "header"
        required: true
        schema:
          type: "string"
      - name: "OptionalHeader"
        in: "header"
        required: false
        schema:
          type: "string"
      responses:
        "200":
          description: "Response Description"
          content:
            application/json:
              schema:
                type: "string"
"""
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Products API
            
            Scenario: Get product by id
              When POST /products
              And request-body
              | id | (string) |
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()
        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        assertThat(openAPIYaml.trim()).isEqualTo(
            """
                ---
                openapi: "3.0.1"
                info:
                  title: "Products API"
                  version: "1"
                paths:
                  /products:
                    post:
                      parameters: []
                      requestBody:
                        content:
                          application/json:
                            schema:
                              required:
                              - "id"
                              properties:
                                id:
                                  type: "string"
                      responses:
                        "200":
                          description: "Response Description"
                          content:
                            application/json:
                              schema:
                                type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body that includes external type definitions`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Products API
            
            Scenario: Get product by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /products
              And request-body
              | id | (string) |
              | address | (Address) |
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()
        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        assertThat(openAPIYaml.trim()).isEqualTo(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Products API"
              version: "1"
            paths:
              /products:
                post:
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          required:
                          - "address"
                          - "id"
                          properties:
                            id:
                              type: "string"
                            address:
                              ${"$"}ref: "#/components/schemas/Address"
                  responses:
                    "200":
                      description: "Response Description"
                      content:
                        application/json:
                          schema:
                            type: "string"
            components:
              schemas:
                Address:
                  required:
                  - "locality"
                  - "street"
                  properties:
                    street:
                      type: "string"
                    locality:
                      type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body containing array of objects`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Products API
            
            Scenario: Get product by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /products
              And request-body
              | id | (string) |
              | address | (Address*) |
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "POST",
                    "/products",
                    body = parsedJSON("""{"id": "123", "address": [{"street": "baker street", "locality": "London"}]}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        assertThat(openAPIYaml.trim()).isEqualTo(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Products API"
              version: "1"
            paths:
              /products:
                post:
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          required:
                          - "address"
                          - "id"
                          properties:
                            id:
                              type: "string"
                            address:
                              type: "array"
                              items:
                                ${"$"}ref: "#/components/schemas/Address"
                  responses:
                    "200":
                      description: "Response Description"
                      content:
                        application/json:
                          schema:
                            type: "string"
            components:
              schemas:
                Address:
                  required:
                  - "locality"
                  - "street"
                  properties:
                    street:
                      type: "string"
                    locality:
                      type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body containing a nullable object`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Products API
            
            Scenario: Get product by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /products
              And request-body
              | id | (string) |
              | address | (Address?) |
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "POST",
                    "/products",
                    body = parsedJSON("""{"id": "123", "address": null}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        assertThat(openAPIYaml.trim()).isEqualTo(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Products API"
              version: "1"
            paths:
              /products:
                post:
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          required:
                          - "address"
                          - "id"
                          properties:
                            id:
                              type: "string"
                            address:
                              ${"$"}ref: "#/components/schemas/Address"
                              nullable: true
                  responses:
                    "200":
                      description: "Response Description"
                      content:
                        application/json:
                          schema:
                            type: "string"
            components:
              schemas:
                Address:
                  required:
                  - "locality"
                  - "street"
                  properties:
                    street:
                      type: "string"
                    locality:
                      type: "string"
                      """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body containing a nullable array`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Products API
            
            Scenario: Get product by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /products
              And request-body
              | id | (string) |
              | address | (Address*?) |
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "POST",
                    "/products",
                    body = parsedJSON("""{"id": "123", "address": [{"street": "baker street", "locality": "London"}, null]}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        assertThat(openAPIYaml.trim()).isEqualTo(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Products API"
              version: "1"
            paths:
              /products:
                post:
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          required:
                          - "address"
                          - "id"
                          properties:
                            id:
                              type: "string"
                            address:
                              type: "array"
                              nullable: true
                              items:
                                ${"$"}ref: "#/components/schemas/Address"
                  responses:
                    "200":
                      description: "Response Description"
                      content:
                        application/json:
                          schema:
                            type: "string"
            components:
              schemas:
                Address:
                  required:
                  - "locality"
                  - "street"
                  properties:
                    street:
                      type: "string"
                    locality:
                      type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body containing an array of nullable values`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Products API
            
            Scenario: Get product by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /products
              And request-body
              | id | (string) |
              | address | (Address?*) |
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "POST",
                    "/products",
                    body = parsedJSON("""{"id": "123", "address": [{"street": "baker street", "locality": "London"}, null]}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        assertThat(openAPIYaml.trim()).isEqualTo(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Products API"
              version: "1"
            paths:
              /products:
                post:
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          required:
                          - "address"
                          - "id"
                          properties:
                            id:
                              type: "string"
                            address:
                              type: "array"
                              items:
                                ${"$"}ref: "#/components/schemas/Address"
                                nullable: true
                  responses:
                    "200":
                      description: "Response Description"
                      content:
                        application/json:
                          schema:
                            type: "string"
            components:
              schemas:
                Address:
                  required:
                  - "locality"
                  - "street"
                  properties:
                    street:
                      type: "string"
                    locality:
                      type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with www-urlencoded request body containing a json field`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              Given type Address
              | street | (string) |
              | locality | (string) |
              And type Person
              | id | (string) |
              | address | (Address) |
              When POST /person
              And form-field person (Person)
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

//        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
//            assertThat(this.matches(
//                HttpRequest(
//                    "POST",
//                    "/person",
//                    formFields = mapOf("person" to """{"id": "123", "address": {"street": "baker street", "locality": "London"}}""")
//                ), HttpResponse.OK("success")
//            )).isTrue
//        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        assertThat(openAPIYaml.trim()).isEqualTo(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Products API"
              version: "1"
            paths:
              /person:
                post:
                  parameters: []
                  requestBody:
                    content:
                      application/x-www-form-urlencoded:
                        schema:
                          required:
                          - "person"
                          properties:
                            person:
                              ${"$"}ref: "#/components/schemas/Person"
                        encoding:
                          person:
                            contentType: "application/json"
                  responses:
                    "200":
                      description: "Response Description"
                      content:
                        application/json:
                          schema:
                            type: "string"
            components:
              schemas:
                Address:
                  required:
                  - "locality"
                  - "street"
                  properties:
                    street:
                      type: "string"
                    locality:
                      type: "string"
                Person:
                  required:
                  - "address"
                  - "id"
                  properties:
                    id:
                      type: "string"
                    address:
                      ${"$"}ref: "#/components/schemas/Address"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST and merge data structures with common names`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              Given type Person
              | address | (string) |
              When POST /person
              And request-body (Person)
              Then status 200
              And response-body (string)

            Scenario: Add Person Details
              Given type Person
              | id | (string) |
              | address | (string) |
              When POST /person
              And request-body (Person)
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "POST",
                    "/person",
                    body = parsedJSON("""{"id": "10", "address": "Baker street"}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        assertThat(openAPIYaml.trim()).isEqualTo(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Products API"
              version: "1"
            paths:
              /person:
                post:
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: "#/components/schemas/Person"
                  responses:
                    "200":
                      description: "Response Description"
                      content:
                        application/json:
                          schema:
                            type: "string"
            components:
              schemas:
                Person:
                  required:
                  - "address"
                  properties:
                    address:
                      type: "string"
                    id:
                      type: "string"
            """.trimIndent()
        )
    }

    fun assertNotFoundInHeaders(header: String, headersPattern: HttpHeadersPattern) {
        assertThat(headersPattern.pattern.keys.map { it.lowercase() }).doesNotContain(header.lowercase())
    }
}