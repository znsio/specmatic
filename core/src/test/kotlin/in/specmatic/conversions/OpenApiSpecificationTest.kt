package `in`.specmatic.conversions

import `in`.specmatic.core.HttpHeadersPattern
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
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

    fun portableComparisonAcrossBuildEnvironments(actual: String, expected: String) {
        assertThat(actual.trimIndent().replace("\"", "")).isEqualTo(expected.trimIndent().replace("\"", ""))
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
    fun temp() {
        val gherkin = """
            Feature: Test
              Scenario: Test
                Given type Data
                | person1 | (Person)  |
                | person2 | (Person_) |
                And type Person
                | id | (number) |
                And type Person_
                | id | (number) |
                When POST /
                And request-body (Data)
                Then status 200
        """

        val yaml = """
            ---
            openapi: 3.0.1
            info:
              title: Test
              version: 1
            paths:
              /:
                post:
                  summary: Test
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: #/components/schemas/Data
                  responses:
                    200:
                      description: Test
            components:
              schemas:
                Data:
                  required:
                  - person1
                  - person2
                  properties:
                    person1:
                      ${"$"}ref: #/components/schemas/Person
                    person2:
                      ${"$"}ref: #/components/schemas/Person
                Person:
                  required:
                  - id
                  properties:
                    id:
                      type: number
                  """.trimIndent()

        portableComparisonAcrossBuildEnvironments(Yaml.mapper().writeValueAsString(parseGherkinStringToFeature(gherkin).toOpenApi()), yaml)
    }

    @Test
    fun `programmatically construct OpenAPI YAML for GET with request headers and path and query params`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Product API

Scenario: Get product by id
  When GET /product/(id:number)/variants/(variantId:number)?tag=(string)
  And request-header Authentication (string)
  And request-header OptionalHeader? (string)
  Then status 200
  And response-body (string)
""".trim()
        )
        val openAPI = feature.toOpenApi()
        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Product API"
              version: "1"
            paths:
              /product/{id}/variants/{variantId}:
                get:
                  summary: "Get product by id"
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
                    200:
                      description: "Get product by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Get person by id
              When POST /person
              And request-body
              | id | (string) |
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()
        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
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
                              - "id"
                              properties:
                                id:
                                  type: "string"
                      responses:
                        200:
                          description: "Get person by id"
                          content:
                            text/plain:
                              schema:
                                type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body that includes external type definitions`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add person by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /person
              And request-body
              | id | (string) |
              | address | (Address) |
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()
        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add person by id"
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
                    200:
                      description: "Add person by id"
                      content:
                        text/plain:
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
            Feature: Person API
            
            Scenario: Get person by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /person
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
                    "/person",
                    body = parsedJSON("""{"id": "123", "address": [{"street": "baker street", "locality": "London"}]}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
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
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
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
            Feature: Person API
            
            Scenario: Get person by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /person
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
                    "/person",
                    body = parsedJSON("""{"id": "123", "address": null}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
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
                          - "address"
                          - "id"
                          properties:
                            id:
                              type: "string"
                            address:
                              ${"$"}ref: "#/components/schemas/Address"
                              nullable: true
                  responses:
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
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
    fun `programmatically construct OpenAPI YAML for POST with JSON request body containing a nullable primitive`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Get person by id
              When POST /person
              And request-body
              | id | (string?) |
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
                    body = parsedJSON("""{"id": null}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
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
                          - "id"
                          properties:
                            id:
                              type: "string"
                              nullable: true
                  responses:
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
                      """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body containing a nullable primitive in string`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Get person by id
              When POST /person
              And request-body
              | id | (number in string?) |
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
                    body = parsedJSON("""{"id": null}""")
                ), HttpResponse.OK("success")
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "POST",
                    "/person",
                    body = parsedJSON("""{"id": "10"}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
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
                          - "id"
                          properties:
                            id:
                              type: "string"
                              nullable: true
                  responses:
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
                      """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with JSON request body containing a nullable array`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Get person by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /person
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
                    "/person",
                    body = parsedJSON("""{"id": "123", "address": [{"street": "baker street", "locality": "London"}, null]}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
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
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
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
            Feature: Person API
            
            Scenario: Get person by id
              Given type Address
              | street | (string) |
              | locality | (string) |
              When POST /person
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
                    "/person",
                    body = parsedJSON("""{"id": "123", "address": [{"street": "baker street", "locality": "London"}, null]}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
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
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
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

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "POST",
                    "/person",
                    formFields = mapOf("person" to """{"id": "123", "address": {"street": "baker street", "locality": "London"}}""")
                ), HttpResponse.OK("success")
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
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
                    200:
                      description: "Add Person"
                      content:
                        text/plain:
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
    fun `merge a POST request with www-urlencoded request body containing a primitive form field`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
                Scenario: Add Data
                  When POST /data
                  And form-field data (string)
                  Then status 200

                Scenario: Add Data
                  When POST /data
                  And form-field data (string)
                  Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "POST",
                    "/data",
                    formFields = mapOf("data" to "hello world")
                ), HttpResponse.OK
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Add Data"
                  parameters: []
                  requestBody:
                    content:
                      application/x-www-form-urlencoded:
                        schema:
                          required:
                          - "data"
                          properties:
                            data:
                              type: "string"
                  responses:
                    200:
                      description: "Add Data"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST and merge JSON request bodies structures with common names`() {
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
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: "#/components/schemas/Person"
                  responses:
                    200:
                      description: "Add Person"
                      content:
                        text/plain:
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

    @Test
    fun `programmatically construct OpenAPI YAML for GET and merge JSON response bodies structures with common names`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              Given type Person
              | address | (string) |
              When GET /person1
              Then status 200
              And response-body (Person)

            Scenario: Add Person Details
              Given type Person
              | id | (string) |
              | address | (string) |
              When GET /person2
              Then status 200
              And response-body (Person)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/person1"
                ), HttpResponse.OK(body = parsedJSON("""{"id": "10", "address": "Baker street"}"""))
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/person2"
                ), HttpResponse.OK(body = parsedJSON("""{"id": "10", "address": "Baker street"}"""))
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/person1"
                ), HttpResponse.OK(body = parsedJSON("""{"address": "Baker street"}"""))
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/person2"
                ), HttpResponse.OK(body = parsedJSON("""{"address": "Baker street"}"""))
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person1:
                get:
                  summary: "Add Person"
                  parameters: []
                  responses:
                    200:
                      description: "Add Person"
                      content:
                        application/json:
                          schema:
                            ${"$"}ref: "#/components/schemas/Person"
              /person2:
                get:
                  summary: "Add Person Details"
                  parameters: []
                  responses:
                    200:
                      description: "Add Person Details"
                      content:
                        application/json:
                          schema:
                            ${"$"}ref: "#/components/schemas/Person"
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

    @Test
    fun `programmatically construct OpenAPI YAML for POST with request type string`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              When POST /person
              And request-body (string)
              Then status 200
            """
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "POST",
                    "/person",
                    body = StringValue("test")
                ), HttpResponse.OK
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters: []
                  requestBody:
                    content:
                      text/plain:
                        schema:
                          type: "string"
                  responses:
                    200:
                      description: "Add Person"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with request body with exact value`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              When POST /person
              And request-body hello
              Then status 200
            """
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "POST",
                    "/person",
                    body = StringValue("hello")
                ), HttpResponse.OK
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters: []
                  requestBody:
                    content:
                      text/plain:
                        schema:
                          type: "string"
                          enum:
                          - "hello"
                  responses:
                    200:
                      description: "Add Person"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with header with exact value`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              When POST /person
              And request-header X-Exact-Value hello
              Then status 200
            """
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "POST",
                    "/person",
                    headers = mapOf("X-Exact-Value" to "hello")
                ), HttpResponse.OK
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters:
                  - name: "X-Exact-Value"
                    in: "header"
                    required: true
                    schema:
                      type: "string"
                      enum:
                      - "hello"
                  responses:
                    200:
                      description: "Add Person"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with request type number`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              When POST /person
              And request-body (number)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "POST",
                    "/person",
                    body = NumberValue(10)
                ), HttpResponse.OK
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters: []
                  requestBody:
                    content:
                      text/plain:
                        schema:
                          type: "number"
                  responses:
                    200:
                      description: "Add Person"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST with type number in string`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              When POST /person
              And request-body
              | id | (number in string) |
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "POST",
                    "/person",
                    body = parsedValue("""{"id": "10"}""")
                ), HttpResponse.OK
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
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
                    200:
                      description: "Add Person"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for PUT`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              When PUT /person
              And request-body
              | id | (number in string) |
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "PUT",
                    "/person",
                    body = parsedValue("""{"id": "10"}""")
                ), HttpResponse.OK
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                put:
                  summary: "Add Person"
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
                    200:
                      description: "Add Person"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST and merge JSON request bodies structures with request type string`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              When POST /person
              And request-body (string)
              Then status 200

            Scenario: Add Person Details
              When POST /person
              And request-body (string)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "POST",
                    "/person",
                    body = StringValue("test")
                ), HttpResponse.OK
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters: []
                  requestBody:
                    content:
                      text/plain:
                        schema:
                          type: "string"
                  responses:
                    200:
                      description: "Add Person"
            """.trimIndent()
        )
    }

    @Test
    fun `programmatically construct OpenAPI YAML for POST and merge JSON response bodies structures with response type number`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              When POST /person
              Then status 200
              And response-body (number)

            Scenario: Add Person Details
              When POST /person
              Then status 200
              And response-body (number)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            this.matchingStub(
                HttpRequest(
                    "POST",
                    "/person"
                ), HttpResponse.OK(NumberValue(10))
            )
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters: []
                  responses:
                    200:
                      description: "Add Person"
                      content:
                        text/plain:
                          schema:
                            type: "number"
            """.trimIndent()
        )
    }

    @Test
    fun `response headers in gherkin are converted to response headers in OpenAIP`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Get Person
              Given type Person
              | address | (string) |
              When GET /person
              Then status 200
              And response-header X-Hello-World (string)
              And response-body (Person)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/person"
                ), HttpResponse.OK(parsedJSON("""{"address": "Baker Street"}""")).copy(headers = mapOf("X-Hello-World" to "hello"))
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                get:
                  summary: "Get Person"
                  parameters: []
                  responses:
                    200:
                      description: "Get Person"
                      headers:
                        X-Hello-World:
                          required: true
                          schema:
                            type: "string"
                      content:
                        application/json:
                          schema:
                            ${"$"}ref: "#/components/schemas/Person"
            components:
              schemas:
                Person:
                  required:
                  - "address"
                  properties:
                    address:
                      type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `JSON response in gherkin is converted to JSON response in OpenAIP`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Get Person
              Given type Person
              | address | (string) |
              When GET /person
              Then status 200
              And response-body (Person)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/person"
                ), HttpResponse.OK(parsedJSON("""{"address": "Baker Street"}"""))
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                get:
                  summary: "Get Person"
                  parameters: []
                  responses:
                    200:
                      description: "Get Person"
                      content:
                        application/json:
                          schema:
                            ${"$"}ref: "#/components/schemas/Person"
            components:
              schemas:
                Person:
                  required:
                  - "address"
                  properties:
                    address:
                      type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `defaults to nullable string for null type in gherkin`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              Given type Person
              | address | (null) |
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
                    body = parsedJSON("""{"address": null}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: "#/components/schemas/Person"
                  responses:
                    200:
                      description: "Add Person"
                      content:
                        text/plain:
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
                      nullable: true
            """.trimIndent()
        )
    }

    @Test
    fun `converts empty json array in gherkin to string array in openapi`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              Given type Person
              | address | [] |
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
                    body = parsedJSON("""{"address": []}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: "#/components/schemas/Person"
                  responses:
                    200:
                      description: "Add Person"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            components:
              schemas:
                Person:
                  required:
                  - "address"
                  properties:
                    address:
                      type: "array"
                      items:
                        type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `payload is a list of nullables`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: Person API
            
            Scenario: Add Person
              Given type Person
              | address | (string?*) |
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
                    body = parsedJSON("""{"address": [null, "Baker Street"]}""")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Add Person"
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: "#/components/schemas/Person"
                  responses:
                    200:
                      description: "Add Person"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            components:
              schemas:
                Person:
                  required:
                  - "address"
                  properties:
                    address:
                      type: "array"
                      items:
                        type: "string"
                        nullable: true
            """.trimIndent()
        )
    }

    @Test
    fun `same request headers from multiple scenarios`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: Get details
              When GET /data
              And request-header X-Data (string)
              Then status 200
              And response-body (string)

            Scenario: Get details
              When GET /data
              And request-header X-Data (string)
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data",
                    headers = mapOf("X-Data" to "data")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                get:
                  summary: "Get details"
                  parameters:
                  - name: "X-Data"
                    in: "header"
                    required: true
                    schema:
                      type: "string"
                  responses:
                    200:
                      description: "Get details"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `same response headers from multiple scenarios`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: Get details
              When GET /data
              Then status 200
              And response-header X-Data (string)
              And response-body (string)

            Scenario: Get details
              When GET /data
              Then status 200
              And response-header X-Data (string)
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data"
                ), HttpResponse.OK("success").copy(headers = mapOf("X-Data" to "data"))
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                get:
                  summary: "Get details"
                  parameters: []
                  responses:
                    200:
                      description: "Get details"
                      headers:
                        X-Data:
                          required: true
                          schema:
                            type: "string"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `different response headers from multiple scenarios with the same method and path`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: Get details
              When GET /data
              Then status 200
              And response-header X-Data-One (string)
              And response-body (string)

            Scenario: Get details
              When GET /data
              Then status 200
              And response-header X-Data-Two (string)
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data"
                ), HttpResponse.OK("success").copy(headers = mapOf("X-Data-One" to "data"))
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data"
                ), HttpResponse.OK("success").copy(headers = mapOf("X-Data-Two" to "data"))
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data"
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                get:
                  summary: "Get details"
                  parameters: []
                  responses:
                    200:
                      description: "Get details"
                      headers:
                        X-Data-One:
                          required: false
                          schema:
                            type: "string"
                        X-Data-Two:
                          required: false
                          schema:
                            type: "string"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `different request headers from multiple scenarios with the same method and path`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: Get details
              When GET /data
              And request-header X-Data-One (string)
              Then status 200
              And response-body (string)

            Scenario: Get details
              When GET /data
              And request-header X-Data-Two (string)
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data",
                    headers = mapOf("X-Data-One" to "data")
                ), HttpResponse.OK("success")
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data",
                    headers = mapOf("X-Data-One" to "data")
                ), HttpResponse.OK("success")
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data",
                    headers = mapOf("X-Data-One" to "data")
                ), HttpResponse.OK("success")
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                get:
                  summary: "Get details"
                  parameters:
                  - name: "X-Data-One"
                    in: "header"
                    required: false
                    schema:
                      type: "string"
                  - name: "X-Data-Two"
                    in: "header"
                    required: false
                    schema:
                      type: "string"
                  responses:
                    200:
                      description: "Get details"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `multiple scenarios with the same query parameters`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: Get details
              When GET /data?param=(string)
              Then status 200
              And response-body (string)

            Scenario: Get details
              When GET /data?param=(string)
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data"
                ), HttpResponse.OK.copy(body = StringValue("success"))
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data",
                    queryParams = mapOf("param" to "data")
                ), HttpResponse.OK.copy(body = StringValue("success"))
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                get:
                  summary: "Get details"
                  parameters:
                  - name: "param"
                    in: "query"
                    schema:
                      type: "string"
                  responses:
                    200:
                      description: "Get details"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `multiple scenarios with the different query parameters`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: Get details
              When GET /data?param1=(string)
              Then status 200
              And response-body (string)

            Scenario: Get details
              When GET /data?param2=(string)
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data"
                ), HttpResponse.OK.copy(body = StringValue("success"))
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data",
                    queryParams = mapOf("param1" to "data")
                ), HttpResponse.OK.copy(body = StringValue("success"))
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data",
                    queryParams = mapOf("param2" to "data")
                ), HttpResponse.OK.copy(body = StringValue("success"))
            )).isTrue
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data",
                    queryParams = mapOf("param1" to "data", "param2" to "data")
                ), HttpResponse.OK.copy(body = StringValue("success"))
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                get:
                  summary: "Get details"
                  parameters:
                  - name: "param1"
                    in: "query"
                    schema:
                      type: "string"
                  - name: "param2"
                    in: "query"
                    schema:
                      type: "string"
                  responses:
                    200:
                      description: "Get details"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()
        )
    }

    @Test
    fun `multiple scenarios with the same form fields containing JSON`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: Get details
              Given type Record
              | id | (number) |
              When POST /data
              And form-field Data (Record)
              Then status 200
              And response-body (string)

            Scenario: Get details
              Given type Record
              | id | (number) |
              When POST /data
              And form-field Data (Record)
              Then status 200
              And response-body (string)
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "POST",
                    "/data",
                    formFields = mapOf("Data" to """{"id": 10}""")
                ), HttpResponse.OK.copy(body = StringValue("success"))
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Get details"
                  parameters: []
                  requestBody:
                    content:
                      application/x-www-form-urlencoded:
                        schema:
                          required:
                          - "Data"
                          properties:
                            Data:
                              ${"$"}ref: "#/components/schemas/Record"
                        encoding:
                          Data:
                            contentType: "application/json"
                  responses:
                    200:
                      description: "Get details"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            components:
              schemas:
                Record:
                  required:
                  - "id"
                  properties:
                    id:
                      type: "number"
            """.trimIndent()
        )
    }

    @Test
    fun `merge multiple scenarios with the different status codes during gherkin-openapi conversion`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: Get details
              When POST /data
              Then status 200

            Scenario: Get details error
              When POST /data
              Then status 500
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "POST",
                    "/data"
                ), HttpResponse.OK
            )).isTrue

            assertThat(
                matches(
                    HttpRequest(
                        "POST",
                        "/data"
                    ), HttpResponse(500)
                )
            ).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Get details"
                  parameters: []
                  responses:
                    200:
                      description: "Get details"
                    500:
                      description: "Get details error"
            """.trimIndent()
        )
    }

    @Test
    fun `empty request and response body should not result in string body when converting from gherkin to OpenAPI `() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: Get details
              When GET /data
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(this.matches(
                HttpRequest(
                    "GET",
                    "/data"
                ), HttpResponse.OK
            )).isTrue
        }

        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        portableComparisonAcrossBuildEnvironments(openAPIYaml,
            """
            ---
            openapi: "3.0.1"
            info:
              title: "API"
              version: "1"
            paths:
              /data:
                get:
                  summary: "Get details"
                  parameters: []
                  responses:
                    200:
                      description: "Get details"
            """.trimIndent()
        )
    }

    private fun assertNotFoundInHeaders(header: String, headersPattern: HttpHeadersPattern) {
        assertThat(headersPattern.pattern.keys.map { it.lowercase() }).doesNotContain(header.lowercase())
    }
}
