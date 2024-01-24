package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.log.CompositePrinter
import `in`.specmatic.core.log.LogMessage
import `in`.specmatic.core.log.LogStrategy
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.mock.NoMatchingScenario
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.stub.HttpStubData
import `in`.specmatic.stub.createStubFromContracts
import `in`.specmatic.test.TestExecutor
import io.ktor.util.reflect.*
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.Base64
import java.util.function.Consumer
import java.util.stream.Stream

fun openAPIToString(openAPI: OpenAPI): String {
    return Yaml.pretty(openAPI)
}

internal class OpenApiSpecificationTest {
    companion object {
        const val OPENAPI_FILE_WITH_YAML_EXTENSION = "openApiTest.yaml"
        const val OPENAPI_FILE_WITH_YML_EXTENSION = "openApiTest.yaml"
        const val OPENAPI_FILE_WITH_REFERENCE = "openApiWithRef.yaml"
        const val PET_OPENAPI_FILE = "Pet.yaml"
        const val SCHEMAS_DIRECTORY = "schemas"

        @JvmStatic
        fun listOfOpenApiFiles(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(OPENAPI_FILE_WITH_YAML_EXTENSION),
                Arguments.of(OPENAPI_FILE_WITH_YML_EXTENSION)
            )
        }
    }

    private fun portableComparisonAcrossBuildEnvironments(actual: String, expected: String) {
        assertThat(actual.trimIndent().replace("\"", "")).isEqualTo(
            expected.removePrefix("---").trimIndent().replace("\"", "")
        )
    }

    @BeforeEach
    fun setup() {
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

        val openApiWithRef = """
openapi: "3.0.0"
info:
  version: 1.0.0
  title: Swagger Petstore
  description: A sample API that uses a petstore as an example to demonstrate features in the OpenAPI 3.0 specification
  termsOfService: http://swagger.io/terms/
  contact:
    name: Swagger API Team
    email: apiteam@swagger.io
    url: http://swagger.io
  license:
    name: Apache 2.0
    url: https://www.apache.org/licenses/LICENSE-2.0.html
servers:
  - url: http://petstore.swagger.io/api
paths:
  /pets:
    get:
      description: get all pets
      operationId: getPets
      responses:
        200:
          description: pet response
          content:
            application/json:
              schema:
                ${"$"}ref: './${SCHEMAS_DIRECTORY}/${PET_OPENAPI_FILE}'
components:
  schemas:
        """.trim()

        val pet = """
Pet:
  type: object
  required:
    - id
    - name
  properties:
    name:
      type: string
    id:
      type: integer
      format: int64
        """.trim()

        listOf(OPENAPI_FILE_WITH_YAML_EXTENSION, OPENAPI_FILE_WITH_YML_EXTENSION).forEach {
            val openApiFile = File(it)
            openApiFile.createNewFile()
            openApiFile.writeText(openAPI)
        }

        val openApiFileWithRef = File(OPENAPI_FILE_WITH_REFERENCE)
        openApiFileWithRef.createNewFile()
        openApiFileWithRef.writeText(openApiWithRef)

        File("./${SCHEMAS_DIRECTORY}").mkdirs()
        val petFile = File("./${SCHEMAS_DIRECTORY}/$PET_OPENAPI_FILE")
        petFile.createNewFile()
        petFile.writeText(pet)
    }

    @AfterEach
    fun teardown() {
        listOf(OPENAPI_FILE_WITH_YAML_EXTENSION, OPENAPI_FILE_WITH_YML_EXTENSION).forEach {
            File(it).delete()
        }
        File(OPENAPI_FILE_WITH_REFERENCE).delete()
        File(SCHEMAS_DIRECTORY).deleteRecursively()
    }

    @Disabled
    @ParameterizedTest
    @MethodSource("listOfOpenApiFiles")
    fun `should generate 200 OK scenarioInfos from openAPI`(openApiFile: String) {
        val openApiSpecification = OpenApiSpecification.fromFile(openApiFile)
        val (scenarioInfos, _) = openApiSpecification.toScenarioInfos()
        assertThat(scenarioInfos.size).isEqualTo(3)
    }

    @Test
    fun `nullable ref`() {
        val gherkin = """
            Feature: Test
              Scenario: Test
                Given type Address
                | street | (string) |
                When POST /user
                And request-body
                | address | (Address?) |
                Then status 200
                And response-body (string)
        """.trimIndent()

        val gherkinToOpenAPI = parseGherkinStringToFeature(gherkin).toOpenApi()
        val yamlFromGherkin = openAPIToString(gherkinToOpenAPI)
        println(yamlFromGherkin)
        val spec = OpenApiSpecification.fromYAML(yamlFromGherkin, "")
        val feature = spec.toFeature()

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "POST",
                    "/user",
                    body = parsedJSON("""{"address": {"street": "Baker Street"}}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "POST",
                    "/user",
                    body = parsedJSON("""{"address": null}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")
    }

    @ParameterizedTest
    @MethodSource("listOfOpenApiFiles")
    fun `should not resolve non ref nested types to Deferred Pattern`(openApiFile: String) {
        val openApiSpecification = OpenApiSpecification.fromFile(openApiFile)
        val (scenarioInfoData, _) = openApiSpecification.toScenarioInfos()
        val nestedTypeWithoutRef = scenarioInfoData.first().patterns.getOrDefault("(NestedTypeWithoutRef)", NullPattern)
        assertThat(containsDeferredPattern(nestedTypeWithoutRef)).isFalse
    }

    @ParameterizedTest
    @MethodSource("listOfOpenApiFiles")
    fun `should resolve ref nested types to Deferred Pattern`(openApiFile: String) {
        val openApiSpecification = OpenApiSpecification.fromFile(openApiFile)
        val (scenarioInfos, _) = openApiSpecification.toScenarioInfos()
        val nestedTypeWithRef = scenarioInfos[4].patterns["(NestedTypeWithRef)"]
        assertThat(containsDeferredPattern(nestedTypeWithRef!!)).isTrue
    }

    private fun containsDeferredPattern(pattern: Pattern): Boolean {
        val innerPattern = pattern.pattern

        if(innerPattern !is Map<*, *>)
            return false

        val childPattern = (innerPattern).values.firstOrNull() ?: return false
        if(childPattern !is Pattern)
            return false

        return if (childPattern.instanceOf(DeferredPattern::class)) true
        else containsDeferredPattern(childPattern)
    }

    @ParameterizedTest
    @MethodSource("listOfOpenApiFiles")
    fun `none of the scenarios should expect the Content-Type header`(openApiFile: String) {
        val openApiSpecification = OpenApiSpecification.fromFile(openApiFile)
        val (scenarioInfos, _) = openApiSpecification.toScenarioInfos()

        for (scenarioInfo in scenarioInfos) {
            assertNotFoundInHeaders(scenarioInfo.httpRequestPattern.headersPattern)
            assertNotFoundInHeaders(scenarioInfo.httpResponsePattern.headersPattern)
        }
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
        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                              ${"$"}ref: '#/components/schemas/Address'
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"id": "123", "address": [{"street": "baker street", "locality": "London"}]}""")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                                ${"$"}ref: '#/components/schemas/Address'
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
    fun `OpenAPI contract where the request body refers to externalised type`() {
        val openAPIYaml = """
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
                          ${"$"}ref: '#/components/schemas/Address'
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

        val feature = OpenApiSpecification.fromYAML(openAPIYaml, "file.yaml").toFeature()

        assertThat(
            feature.matches(
                HttpRequest(
                    "POST",
                    "/person",
                    body = parsedJSON("""{"street": "baker street", "locality": "London"}""")
                ), HttpResponse.ok("success")
            )
        ).isTrue
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"id": "123", "address": null}""")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                              oneOf:
                              - properties: {}
                                nullable: true
                              - ${"$"}ref: '#/components/schemas/Address'
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"id": null}""")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"id": null}""")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"id": "10"}""")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            with(
                this.scenarios.first().matchesMock(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"id": "123", "address": [{"street": "baker street", "locality": "London"}]}""")
                    ), HttpResponse.ok("success")
                )
            ) {
                assertThat(this).isInstanceOf(Result.Success::class.java)
            }

            with(
                this.scenarios.first().matchesMock(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"id": "123", "address": null}""")
                    ), HttpResponse.ok("success")
                )
            ) {

                assertThat(this).isInstanceOf(Result.Success::class.java)
            }
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                                ${"$"}ref: '#/components/schemas/Address'
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"id": "123", "address": [{"street": "baker street", "locality": "London"}, null]}""")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                                oneOf:
                                - properties: {}
                                  nullable: true
                                - ${"$"}ref: '#/components/schemas/Address'
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
                ), HttpResponse.ok("success")
            )
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                              ${"$"}ref: '#/components/schemas/Person'
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
                      ${"$"}ref: '#/components/schemas/Address'
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

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"id": "10", "address": "Baker street"}""")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                          ${"$"}ref: '#/components/schemas/Person'
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/person1"
                    ), HttpResponse.ok(body = parsedJSON("""{"id": "10", "address": "Baker street"}"""))
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/person2"
                    ), HttpResponse.ok(body = parsedJSON("""{"id": "10", "address": "Baker street"}"""))
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/person1"
                    ), HttpResponse.ok(body = parsedJSON("""{"address": "Baker street"}"""))
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/person2"
                    ), HttpResponse.ok(body = parsedJSON("""{"address": "Baker street"}"""))
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                            ${"$"}ref: '#/components/schemas/Person'
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
                            ${"$"}ref: '#/components/schemas/Person'
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

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                ), HttpResponse.ok(NumberValue(10))
            )
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/person"
                    ),
                    HttpResponse.ok(parsedJSON("""{"address": "Baker Street"}"""))
                        .copy(headers = mapOf("X-Hello-World" to "hello"))
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                            ${"$"}ref: '#/components/schemas/Person'
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/person"
                    ), HttpResponse.ok(parsedJSON("""{"address": "Baker Street"}"""))
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                            ${"$"}ref: '#/components/schemas/Person'
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"address": null}""")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                          ${"$"}ref: '#/components/schemas/Person'
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""{"address": []}""")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                          ${"$"}ref: '#/components/schemas/Person'
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
            val result: Result = this.scenarios.first().matchesMock(
                HttpRequest(
                    "POST",
                    "/person",
                    body = parsedJSON("""{"address": [null, "Baker Street"]}""")
                ), HttpResponse.ok("success")
            )

            assertThat(result).isInstanceOf(Result.Success::class.java)
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                          ${"$"}ref: '#/components/schemas/Person'
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
                        oneOf:
                        - properties: {}
                          nullable: true
                        - type: "string"
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data",
                        headers = mapOf("X-Data" to "data")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data"
                    ), HttpResponse.ok("success").copy(headers = mapOf("X-Data" to "data"))
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data"
                    ), HttpResponse.ok("success").copy(headers = mapOf("X-Data-One" to "data"))
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data"
                    ), HttpResponse.ok("success").copy(headers = mapOf("X-Data-Two" to "data"))
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data"
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data",
                        headers = mapOf("X-Data-One" to "data")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data",
                        headers = mapOf("X-Data-One" to "data")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data",
                        headers = mapOf("X-Data-One" to "data")
                    ), HttpResponse.ok("success")
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data"
                    ), HttpResponse.OK.copy(body = StringValue("success"))
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data",
                        queryParams = mapOf("param" to "data")
                    ), HttpResponse.OK.copy(body = StringValue("success"))
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data"
                    ), HttpResponse.OK.copy(body = StringValue("success"))
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data",
                        queryParams = mapOf("param1" to "data")
                    ), HttpResponse.OK.copy(body = StringValue("success"))
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data",
                        queryParams = mapOf("param2" to "data")
                    ), HttpResponse.OK.copy(body = StringValue("success"))
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data",
                        queryParams = mapOf("param1" to "data", "param2" to "data")
                    ), HttpResponse.OK.copy(body = StringValue("success"))
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data",
                        formFields = mapOf("Data" to """{"id": 10}""")
                    ), HttpResponse.OK.copy(body = StringValue("success"))
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
                              ${"$"}ref: '#/components/schemas/Record'
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data"
                    ), HttpResponse.OK
                )
            ).isTrue

            assertThat(
                matches(
                    HttpRequest(
                        "POST",
                        "/data"
                    ), HttpResponse(500)
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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
            assertThat(
                this.matches(
                    HttpRequest(
                        "GET",
                        "/data"
                    ), HttpResponse.OK
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
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

    @Test
    fun `payload type conversion when there are multiple request bodies named RequestBody should add prefixes to the type name and keep the request bodies separate`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: API 1
              Given type RequestBody
              | hello | (string) |
              When POST /data1
              And request-body (RequestBody)
              Then status 200

            Scenario: API 2
              Given type RequestBody
              | world | (string) |
              When POST /data2
              And request-body (RequestBody)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data1",
                        body = parsedJSON("""{"hello": "Jill"}""")
                    ), HttpResponse.OK
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data2",
                        body = parsedJSON("""{"world": "Jack"}""")
                    ), HttpResponse.OK
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
            """
            ---
            openapi: 3.0.1
            info:
              title: API
              version: 1
            paths:
              /data1:
                post:
                  summary: API 1
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/Data1_RequestBody'
                  responses:
                    200:
                      description: API 1
              /data2:
                post:
                  summary: API 2
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/Data2_RequestBody'
                  responses:
                    200:
                      description: API 2
            components:
              schemas:
                Data1_RequestBody:
                  required:
                  - hello
                  properties:
                    hello:
                      type: string
                Data2_RequestBody:
                  required:
                  - world
                  properties:
                    world:
                      type: string
              """.trimIndent()
        )
    }

    @Test
    fun `scenario 1 has string and scenario 2 has null and the paths are the same`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: API 1
              Given type RequestBody
              | hello | (string) |
              When POST /data
              And request-body (RequestBody)
              Then status 200

            Scenario: API 2
              Given type RequestBody
              | hello | (null) |
              When POST /data
              And request-body (RequestBody)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data",
                        body = parsedJSON("""{"hello": "Jill"}""")
                    ), HttpResponse.OK
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data",
                        body = parsedJSON("""{"hello": null}""")
                    ), HttpResponse.OK
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
            """
            ---
            openapi: 3.0.1
            info:
              title: API
              version: 1
            paths:
              /data:
                post:
                  summary: API 1
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/Data_RequestBody'
                  responses:
                    200:
                      description: API 1
            components:
              schemas:
                Data_RequestBody:
                  required:
                  - hello
                  properties:
                    hello:
                      type: string
                      nullable: true
              """.trimIndent()
        )
    }

    @Test
    fun `scenario 1 has an object and scenario 2 has null but the paths are the same`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: API 1
              Given type RequestBody
              | hello | (Hello) |
              And type Hello
              | world | (string) |
              When POST /data
              And request-body (RequestBody)
              Then status 200

            Scenario: API 2
              Given type RequestBody
              | hello | (null) |
              When POST /data
              And request-body (RequestBody)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data",
                        body = parsedJSON("""{"hello": {"world": "jill"}}""")
                    ), HttpResponse.OK
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data",
                        body = parsedJSON("""{"hello": null}""")
                    ), HttpResponse.OK
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
            """
            ---
            openapi: 3.0.1
            info:
              title: API
              version: 1
            paths:
              /data:
                post:
                  summary: API 1
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/Data_RequestBody'
                  responses:
                    200:
                      description: API 1
            components:
              schemas:
                Hello:
                  required:
                  - world
                  properties:
                    world:
                      type: string
                Data_RequestBody:
                  required:
                  - hello
                  properties:
                    hello:
                      oneOf:
                      - properties: {}
                        nullable: true
                      - ${"$"}ref: '#/components/schemas/Hello'
            """.trimIndent()
        )
    }

    @Test
    fun `scenario 1 has an object and scenario two has a nullable object of the same type but the paths are the same`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: API 1
              Given type RequestBody
              | hello | (Hello) |
              And type Hello
              | world | (string) |
              When POST /data
              And request-body (RequestBody)
              Then status 200

            Scenario: API 2
              Given type RequestBody
              | hello | (Hello?) |
              When POST /data
              And request-body (RequestBody)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data",
                        body = parsedJSON("""{"hello": {"world": "jill"}}""")
                    ), HttpResponse.OK
                )
            ).isTrue
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data",
                        body = parsedJSON("""{"hello": null}""")
                    ), HttpResponse.OK
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
            """
            ---
            openapi: 3.0.1
            info:
              title: API
              version: 1
            paths:
              /data:
                post:
                  summary: API 1
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/Data_RequestBody'
                  responses:
                    200:
                      description: API 1
            components:
              schemas:
                Hello:
                  required:
                  - world
                  properties:
                    world:
                      type: string
                Data_RequestBody:
                  required:
                  - hello
                  properties:
                    hello:
                      oneOf:
                      - properties: {}
                        nullable: true
                      - ${"$"}ref: '#/components/schemas/Hello'
            """.trimIndent()
        )
    }

    @Test
    fun `lookup string value in gherkin should result in a string type in yaml`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: API
              When POST /data
              And request-body (RequestBody: string)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data",
                        body = StringValue("Hello world")
                    ), HttpResponse.OK
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
            """
            ---
            openapi: 3.0.1
            info:
              title: API
              version: 1
            paths:
              /data:
                post:
                  summary: API
                  parameters: []
                  requestBody:
                    content:
                      text/plain:
                        schema:
                          type: string
                  responses:
                    200:
                      description: API
            """.trimIndent()
        )
    }

    @Test
    fun `recursive array ref`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: API
              Given type Data
              | id | (number) |
              | data? | (Data*) |
              When POST /data
              And request-body (Data)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data",
                        body = parsedJSON("""{"id": 10}""")
                    ), HttpResponse.OK
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
            """
            ---
            openapi: 3.0.1
            info:
              title: API
              version: 1
            paths:
              /data:
                post:
                  summary: API
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/Data'
                  responses:
                    200:
                      description: API
            components:
              schemas:
                Data:
                  required:
                  - id
                  properties:
                    id:
                      type: number
                    data:
                      type: array
                      items:
                        ${"$"}ref: '#/components/schemas/Data'
            """.trimIndent()
        )
    }

    @Test
    fun `clone request pattern with example of body type should pick up the example`() {
        val openAPI = openAPIToString(
            parseGherkinStringToFeature(
                """
            Feature: API
            
            Scenario: API
              Given type Data
              | id | (number) |
              When POST /data
              And request-body (Data)
              Then status 200
            """.trimIndent()
            ).toOpenApi()
        )

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val data = """{"id": 10}"""
        val row = Row(columnNames = listOf("(Data)"), values = listOf(data))
        val resolver = feature.scenarios.single().resolver

        val newPatterns = feature.scenarios.single().httpRequestPattern.newBasedOn(row, resolver)

        assertThat((newPatterns.single().body as ExactValuePattern).pattern as JSONObjectValue).isEqualTo(
            parsedValue(
                data
            )
        )
    }

    @Test
    fun `handle object inside array inside object correctly`() {
        val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    post:
      summary: API
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              ${"$"}ref: '#/components/schemas/Data'
      responses:
        200:
          description: API
components:
  schemas:
    Data:
      properties:
        data:
          type: array
          items:
            type: object
            properties:
              id:
                type: integer
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request = HttpRequest("POST", "/data", body = parsedValue("""{"data": [{"id": 10}]}"""))
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        println(stub.requestType)

        assertThat(stub.requestType.method).isEqualTo("POST")

    }

    @Test
    fun `support path parameter as enum inline`() {
        val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /permissions/state/{state}:
    get:
      parameters:
      - name: state
        in: path
        schema:
          type: string
          enum:
          - ALLOW
          - DENY
      responses:
        200:
          description: API
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request = HttpRequest("GET", "/permissions/state/ALLOW")
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        println(stub.requestType)

        assertThat(stub.requestType.method).isEqualTo("GET")
        assertThat(stub.response.status).isEqualTo(200)
    }

    @Test
    fun `support path parameter as enum reference`() {
        val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /permissions/state/{state}:
    get:
      parameters:
      - ${'$'}ref: '#/components/parameters/stateParam'
      responses:
        200:
          description: API
components:
  parameters:
    stateParam:
      name: state
      in: path
      schema:
        type: string
        enum:
        - ALLOW
        - DENY
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request = HttpRequest("GET", "/permissions/state/ALLOW")
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        println(stub.requestType)

        assertThat(stub.requestType.method).isEqualTo("GET")
        assertThat(stub.response.status).isEqualTo(200)
    }

    @Test
    fun `support dictionary object type in request body with data structure as reference`() {
        val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    post:
      summary: API
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              additionalProperties:
                ${"$"}ref: Data
      responses:
        200:
          description: API
components:
  schemas:
    Data:
      type: object
      properties:
        name:
          type: string
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request =
            HttpRequest("POST", "/data", body = parsedValue("""{"10": {"name": "Jill"}, "20": {"name": "Jack"}}"""))
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        println(stub.requestType)

        assertThat(stub.requestType.method).isEqualTo("POST")
        assertThat(stub.response.status).isEqualTo(200)
    }

    @Test
    fun `support dictionary object type in request body with inline data structure`() {
        val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    post:
      summary: API
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              additionalProperties:
                type: object
                properties:
                  name:
                    type: string
      responses:
        200:
          description: API
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request =
            HttpRequest("POST", "/data", body = parsedValue("""{"10": {"name": "Jill"}, "20": {"name": "Jack"}}"""))
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        println(stub.requestType)

        assertThat(stub.requestType.method).isEqualTo("POST")
        assertThat(stub.response.status).isEqualTo(200)
    }

    @Test
    fun `support dictionary object type as JSON value`() {
        val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    post:
      summary: API
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                Data:
                  type: object
                  additionalProperties:
                    ${"$"}ref: Person
      responses:
        200:
          description: API
components:
  schemas:
    Person:
      type: object
      properties:
        name:
          type: string
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request = HttpRequest(
            "POST",
            "/data",
            body = parsedValue("""{"Data": {"10": {"name": "Jill"}, "20": {"name": "Jack"}}}""")
        )
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        println(stub.requestType)

        assertThat(stub.requestType.method).isEqualTo("POST")
        assertThat(stub.response.status).isEqualTo(200)

    }

    @Test
    fun `support dictionary object type with composed oneOf value`() {
        val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    post:
      summary: API
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                myValues:
                  type: object
                  additionalProperties:
                    oneOf:
                    - ${"$"}ref: Person
                    - ${"$"}ref: Alien
      responses:
        200:
          description: API
components:
  schemas:
    Person:
      type: object
      properties:
        name:
          type: string
    Alien:
      type: object
      properties:
        moniker:
          type: string
        homePlanet:
          type: string
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request = HttpRequest(
            "POST",
            "/data",
            body = parsedValue("""{"myValues": {"10": {"name": "Jill"}, "20": {"moniker": "Vin", "homePlanet": "Scadrial"}}}""")
        )
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        println(stub.requestType)

        assertThat(stub.requestType.method).isEqualTo("POST")
        assertThat(stub.response.status).isEqualTo(200)

    }

    @Test
    fun `support dictionary object type in request body with inline fixed keys`() {
        val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    post:
      summary: API
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              additionalProperties:
                type: string
      responses:
        200:
          description: API
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request =
            HttpRequest("POST", "/data", body = parsedValue("""{"10": "Jill", "20": "Jack"}"""))
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        println(stub.requestType)

        assertThat(stub.requestType.method).isEqualTo("POST")
        assertThat(stub.response.status).isEqualTo(200)
    }

    @Nested
    inner class WhenAdditionalPropertiesIsFalse {
        private val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    post:
      summary: API
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                id:
                  type: integer
              additionalProperties: false
      responses:
        200:
          description: API
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        @Test
        fun `an object with no additional properties should match the specification`() {
            val request =
                HttpRequest("POST", "/data", body = parsedValue("""{"id": 10}"""))
            val response = HttpResponse.OK

            val stub: HttpStubData = feature.matchingStub(request, response)
            assertThat(stub.requestType.matches(request, Resolver())).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `an object with additional properties should not match the specification`() {
            val invalidRequest =
                HttpRequest("POST", "/data", body = parsedValue("""{"id": 10, "name": "Jill"}"""))
            val response = HttpResponse.OK

            assertThatThrownBy { feature.matchingStub(invalidRequest, response) }
                .isInstanceOf(NoMatchingScenario::class.java)
                .hasMessageContaining("REQUEST.BODY.name")
        }
    }


    @Nested
    inner class WhenAdditionalPropertiesIsTrue {
        private val openAPI =
            """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    post:
      summary: API
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              additionalProperties: true
      responses:
        200:
          description: API
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        @Test
        fun `an object with string keys with values of any type should meet the specification`() {
            val request =
                HttpRequest(
                    "POST",
                    "/data",
                    body = parsedValue("""{"id": 10, "address": {"street": "Link Road", "city": "Mumbai", "country": "India"}}""")
                )
            val response = HttpResponse.OK

            val stub: HttpStubData = feature.matchingStub(request, response)
            assertThat(stub.requestType.matches(request, Resolver())).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `an object with string keys with some values set as null should match the specification`() {
            val request =
                HttpRequest("POST", "/data", body = parsedValue("""{"id": 10, "address": null}"""))
            val response = HttpResponse.OK

            val stub: HttpStubData = feature.matchingStub(request, response)
            assertThat(stub.requestType.matches(request, Resolver())).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `a string value should not match the specification`() {
            val invalidRequest =
                HttpRequest("POST", "/data", body = StringValue("some data"))
            val response = HttpResponse.OK

            assertThatThrownBy { feature.matchingStub(invalidRequest, response) }
                .isInstanceOf(NoMatchingScenario::class.java)
                .hasMessageContaining("Expected json object, actual was some data")
        }
    }

    @Test
    fun `conversion supports dictionary type`() {
        val gherkin = """
            Feature: Test
              Scenario: Test
                Given type Data
                | name | (string) |
                When GET /
                Then status 200
                And response-body (dictionary string Data)
        """.trimIndent()

        val feature = parseGherkinStringToFeature(gherkin)
        val openAPI = feature.toOpenApi()

        val openAPIYaml = openAPIToString(openAPI)

        println(openAPIYaml)

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            val request = HttpRequest(
                "GET",
                "/"
            )
            val response = HttpResponse.ok(
                body = parsedJSON("""{"10": {"name": "Jane"}}""")
            )

            assertThat(
                this.matches(
                    request,
                    response
                )
            ).isTrue
        }

    }

    @Test
    fun `should resolve ref to another file`() {
        val openApiSpecification = OpenApiSpecification.fromFile(OPENAPI_FILE_WITH_REFERENCE)
        val (scenarioInfos, _) = openApiSpecification.toScenarioInfos()
        assertThat((scenarioInfos).size).isEqualTo(1)
        assertThat(openApiSpecification.patterns["(Pet)"]).isNotNull
    }

    private fun assertNotFoundInHeaders(headersPattern: HttpHeadersPattern) {
        assertThat(headersPattern.pattern.keys.map { it.lowercase() }).doesNotContain(CONTENT_TYPE.lowercase())
    }

    @Nested
    inner class XML {
        @Test
        fun `basic xml contract`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/users':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: user
                          properties:
                            id:
                              type: integer
                          required:
                            - id
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<user><id>10</id></user>"""

            assertMatchesSnippet(xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with attributes`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/users':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: user
                          properties:
                            id:
                              type: integer
                            name:
                              type: string
                              xml:
                                attribute: true
                          required:
                            - id
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<user name="John Doe"><id>10</id></user>"""

            assertMatchesSnippet(xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract shows error when compulsory node is not available`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/users':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: user
                          properties:
                            id:
                              type: integer
                            name:
                              type: string
                          required:
                            - id
                            - name
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()
            val xmlSnippet = """<user><id>10</id></user>"""
            val request = HttpRequest("POST", "/users", body = parsedValue(xmlSnippet))
            assertThatThrownBy {
                xmlFeature.matchingStub(
                    request,
                    HttpResponse.OK
                )
            }.isInstanceOf(NoMatchingScenario::class.java)
        }

        @Test
        fun `xml contract with optional node`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/users':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: user
                          properties:
                            id:
                              type: integer
                            name:
                              type: string
                          required:
                            - id
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<user><id>10</id></user>"""

            assertMatchesSnippet(xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with attributes in child node`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/users':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: user
                          properties:
                            id:
                              type: integer
                            address:
                              type: object
                              properties:
                                street:
                                  type: string
                                pincode:
                                  type: string
                                  xml:
                                    attribute: true
                              required:
                                - street
                                - pincode
                          required:
                            - id
                            - address
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet =
                """<user><id>10</id><address pincode="101010"><street>Baker street</street></address></user>"""

            assertMatchesSnippet(xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with unwrapped xml node array marked required`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: products
                          properties:
                            id:
                              type: array
                              items:
                                type: number
                          required:
                            - id
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<products><id>10</id><id>10</id></products>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with unwrapped xml node array which is not marked required`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: products
                          properties:
                            id:
                              type: array
                              items:
                                type: number
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<products><id>10</id><id>10</id></products>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with unwrapped xml node array that specifies it's own name`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: products
                          properties:
                            id:
                              type: array
                              items:
                                type: number
                                xml:
                                  name: productid
                          required:
                            - id
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<products><productid>10</productid><productid>10</productid></products>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with wrapped xml node array that specifies wrapper name`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: array
                          items:
                            type: number
                          xml:
                            wrapped: true
                            name: products
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<products><products>10</products><products>10</products></products>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with wrapped xml node array that does not specify wrapper name`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          ${'$'}ref: '#/components/schemas/products'
            components:
              schemas:
                products:
                  type: array
                  items:
                    type: number
                    xml:
                      name: id
                  xml:
                    wrapped: true
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<products><id>10</id><id>10</id></products>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with wrapped xml node array that changes wrapper name but not member element name`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          ${'$'}ref: '#/components/schemas/product'
            components:
              schemas:
                product:
                  type: array
                  items:
                    type: number
                  xml:
                    wrapped: true
                    name: products
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<products><products>10</products><products>10</products></products>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with wrapped xml node array that sets the array name without setting wrapper to true`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          ${'$'}ref: '#/components/schemas/product'
            components:
              schemas:
                product:
                  type: object
                  properties:
                    productdata:
                      type: array
                      items:
                        type: number
                      xml:
                        name: products
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<product><productdata>10</productdata><productdata>10</productdata></product>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with wrapped xml node array defined in an object that sets the array name and sets wrapper to true`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          ${'$'}ref: '#/components/schemas/productdata'
            components:
              schemas:
                productdata:
                  type: object
                  properties:
                    productinner:
                      type: array
                      items:
                        type: number
                      xml:
                        name: products
                        wrapped: true
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet =
                """<productdata><products><products>10</products><products>10</products></products></productdata>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with body defined by ref having xml name`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          ${'$'}ref: '#/components/schemas/RequestBody'
            components:
              schemas:
                RequestBody:
                  type: object
                  xml:
                    name: products
                  properties:
                    id:
                      type: number
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<products><id>10</id></products>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with body defined by ref having no xml name`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          ${'$'}ref: '#/components/schemas/products'
            components:
              schemas:
                products:
                  type: object
                  properties:
                    id:
                      type: number
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<products><id>10</id></products>"""

            assertMatchesSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with xml response body`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/cart':
                get:
                  responses:
                    '200':
                      description: OK
                      content:
                        application/xml:
                          schema:
                            ${'$'}ref: '#/components/schemas/products'
            components:
                  schemas:
                    products:
                      type: object
                      properties:
                        id:
                          type: number
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<products><id>10</id></products>"""

            assertMatchesResponseSnippet("/cart", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with xml ref within an xml payload`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/user':
                get:
                  responses:
                    '200':
                      description: OK
                      content:
                        application/xml:
                          schema:
                            type: object
                            xml:
                              name: user
                            properties:
                              id:
                                type: number
                              company:
                                type: object
                                properties:
                                  id:
                                    type: number
                                  address:
                                    ${"$"}ref: '#/components/schemas/AddressData' 
            components:
                  schemas:
                    AddressData:
                      type: object
                      properties:
                        flat:
                          type: string
                        street:
                          type: string
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet =
                """<user><id>10</id><company><id>100</id><address><flat>221B</flat><street>Baker Street</street></address></company></user>"""

            assertMatchesResponseSnippet("/user", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with xml ref within an xml payload nested at the second level`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/user':
                get:
                  responses:
                    '200':
                      description: OK
                      content:
                        application/xml:
                          schema:
                            type: object
                            xml:
                              name: user
                            properties:
                              id:
                                type: number
                              address:
                                ${"$"}ref: '#/components/schemas/AddressData' 
            components:
                  schemas:
                    AddressData:
                      type: object
                      properties:
                        flat:
                          type: string
                        street:
                          type: string
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet =
                """<user><id>10</id><address><flat>221B</flat><street>Baker Street</street></address></user>"""

            assertMatchesResponseSnippet("/user", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with array items specified as ref`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/user':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: users
                          properties:
                            productid:
                              type: array
                              xml:
                                name: user
                              items:
                                ${"$"}ref: '#/components/schemas/UserData'
            components:
              schemas:
                UserData:
                  type: object
                  properties:
                    id:
                      type: number
                    name:
                      type: string
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet =
                """<users><user><id>10</id><name>John Doe</name></user><user><id>20</id><name>Jane Doe</name></user></users>"""

            assertMatchesSnippet("/user", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with value specified as ref pointing to an array type`() {
            val xmlContract1 = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/user':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: users
                          properties:
                            user:
                              ${'$'}ref: '#/components/schemas/UserArray'
                              type: array
                              items:
                                ${"$"}ref: '#/components/schemas/UserData'
            components:
              schemas:
                UserData:
                  type: object
                  properties:
                    id:
                      type: number
                    name:
                      type: string
                UserArray:
                  type: array
                  items:
                    ${"$"}ref: '#/components/schemas/UserData'
                  xml:
                    name: user
        """.trimIndent()

            val xmlContract2 = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/user':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: users
                          properties:
                            user:
                              ${'$'}ref: '#/components/schemas/UserArray'
                              type: array
                              xml:
                                name: user
                              items:
                                ${"$"}ref: '#/components/schemas/UserData'
            components:
              schemas:
                UserData:
                  type: object
                  properties:
                    id:
                      type: number
                    name:
                      type: string
                UserArray:
                  type: array
                  items:
                    ${"$"}ref: '#/components/schemas/UserData'
        """.trimIndent()

            for (xmlContract in listOf(xmlContract1, xmlContract2)) {
                val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

                val xmlSnippet =
                    """<users><user><id>10</id><name>John Doe</name></user><user><id>20</id><name>Jane Doe</name></user></users>"""

                assertMatchesSnippet("/user", xmlSnippet, xmlFeature)
            }
        }

        @Test
        fun `xml contract with recursive type definition`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/user':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          ${'$'}ref: '#/components/schemas/user'
            components:
              schemas:
                user:
                  type: object
                  properties:
                    id:
                      type: number
                    name:
                      type: string
                    next:
                      ${'$'}ref: '#/components/schemas/user'
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet =
                """<user><id>10</id><name>John Doe</name><next><id>20</id><name>Jane Doe</name></next></user>"""

            assertMatchesSnippet("/user", xmlSnippet, xmlFeature)
        }

        @Test
        fun `xml contract with prefix`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/user':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: user
                            prefix: test
                          properties:
                            id:
                              type: number
                            name:
                              type: string
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet = """<test:user><id>10</id><name>John Doe</name></test:user>"""

            assertMatchesSnippet("/user", xmlSnippet, xmlFeature)

            val body = xmlFeature.scenarios.first().httpRequestPattern.body as XMLPattern
            assertThat(body.pattern.realName).isEqualTo("test:user")
        }

        @Test
        fun `xml contract with prefix and namespace`() {
            val xmlContract = """
            openapi: 3.0.3
            info:
              title: test-xml
              version: '1.0'
            paths:
              '/user':
                post:
                  responses:
                    '200':
                      description: OK
                  requestBody:
                    content:
                      application/xml:
                        schema:
                          type: object
                          xml:
                            name: user
                            prefix: test
                            namespace: 'http://helloworld.com'
                          properties:
                            id:
                              type: number
                            name:
                              type: string
        """.trimIndent()

            val xmlFeature = OpenApiSpecification.fromYAML(xmlContract, "").toFeature()

            val xmlSnippet =
                """<test:user xmlns:test="http://helloworld.com"><id>10</id><name>John Doe</name></test:user>"""

            assertMatchesSnippet("/user", xmlSnippet, xmlFeature)

            val body = xmlFeature.scenarios.first().httpRequestPattern.body as XMLPattern
            assertThat(body.pattern.realName).isEqualTo("test:user")
            val testNamespaceAttribute = body.pattern.attributes.getValue("xmlns:test") as ExactValuePattern
            assertThat(testNamespaceAttribute.pattern.toStringLiteral()).isEqualTo("http://helloworld.com")
        }

        @Test
        fun `run contract tests from an OpenAPI XML spec`(@TempDir dir: File) {
            val contractString = """
                openapi: 3.0.3
                info:
                  title: test-xml
                  version: '1.0'
                paths:
                  '/users':
                    post:
                      responses:
                        '200':
                          description: OK
                      requestBody:
                        content:
                          application/xml:
                            schema:
                              ${'$'}ref: '#/components/schemas/user'
                components:
                  schemas:
                    user:
                      type: object
                      properties:
                        id:
                          type: integer
                      required:
                        - id
            """.trimIndent()

            val contractFile = dir.canonicalFile.resolve("contract.yaml")
            contractFile.writeText(contractString)

            val wrapperSpecString = """
                Feature: Test
                  Background:
                    Given openapi ./contract.yaml
                    
                  Scenario Outline: Test
                    When POST /users
                    Then status 200
                    
                    Examples:
                    | (user)                   |
                    | <user><id>10</id></user> |
            """.trimIndent()

            val wrapperSpecFile = dir.canonicalFile.resolve("contract.spec")
            wrapperSpecFile.writeText(wrapperSpecString)

            val feature: Feature = parseContractFileToFeature(wrapperSpecFile.path)
            var state = "not_called"

            val result: Results = feature.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    println(request.body.toStringLiteral())
                    assertThat(request.body.toStringLiteral()).isEqualTo("""<user><id>10</id></user>""")
                    state = "called"
                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })

            println(result.report())

            assertThat(result.success()).isTrue()

            assertThat(state).isEqualTo("called")
        }

        private fun assertMatchesSnippet(xmlSnippet: String, xmlFeature: Feature) {
            assertMatchesSnippet("/users", xmlSnippet, xmlFeature)
        }

        private fun assertMatchesSnippet(path: String, xmlSnippet: String, xmlFeature: Feature) {
            val request = HttpRequest("POST", path, body = parsedValue(xmlSnippet))
            val stubData = xmlFeature.matchingStub(request, HttpResponse.OK)

            val stubMatchResult =
                stubData.requestType.body.matches(parsedValue(xmlSnippet), xmlFeature.scenarios.first().resolver)

            assertThat(stubMatchResult).isInstanceOf(Result.Success::class.java)
        }

        private fun assertMatchesResponseSnippet(path: String, xmlSnippet: String, xmlFeature: Feature) {
            val request = HttpRequest("GET", path)
            val stubData = xmlFeature.matchingStub(request, HttpResponse.ok(body = parsedValue(xmlSnippet)))

            val stubMatchResult =
                stubData.responsePattern.body.matches(parsedValue(xmlSnippet), xmlFeature.scenarios.first().resolver)

            assertThat(stubMatchResult).isInstanceOf(Result.Success::class.java)
        }

    }

    @Test
    fun `support for exporting values from a wrapper spec file`(@TempDir tempDir: File) {
        val openAPI = """
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
                        application/json:
                          schema:
                            type: object
                            required:
                              - id
                            properties:
                              id:
                                type: integer
            """.trimIndent()

        val openAPIFile = tempDir.resolve("person.yaml")
        openAPIFile.writeText(openAPI)

        val spec = """
            Feature: Person API
            
              Background:
                Given openapi ./person.yaml
                
              Scenario Outline: Person API
                When POST /person
                Then status 200
                And export id = response-body.id
                
                Examples:
                | id |
                | 10 |
        """.trimIndent()

        val specFile = tempDir.resolve("person.spec")
        specFile.writeText(spec)

        val feature = parseContractFileToFeature(specFile)

        val testScenario = feature.generateContractTestScenarios(emptyList()).single()

        assertThat(testScenario.bindings).containsEntry("id", "response-body.id")
    }

    @Test
    fun `support for multipart form data tests`(@TempDir tempDir: File) {
        val openAPI = """
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data_csv:
                post:
                  summary: "Save data"
                  parameters: []
                  requestBody:
                    ${'$'}ref: '#/components/requestBodies/SaveData'
                  responses:
                    200:
                      description: "Get product by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            components:
              requestBodies:
                SaveData:
                  content:
                      multipart/form-data:
                        encoding:
                          csv:
                            contentType: text/csv
                        schema:
                          ${'$'}ref: '#/components/schemas/CsvContent'
              schemas:
                CsvContent:
                  type: object
                  required:
                    - "csv"
                  properties:
                    csv:
                      type: "string"
            """.trimIndent()

        val openAPIFile = tempDir.resolve("data.yaml")
        openAPIFile.writeText(openAPI)

        val csvFile = tempDir.resolve("data.csv")
        val csvFileContent = "1,2,3"
        csvFile.writeText(csvFileContent)

        val spec = """
            Feature: Data API
            
              Background:
                Given openapi data.yaml
                
              Scenario Outline: Save data
                When POST /data_csv
                Then status 200
                
                Examples:
                | csv         |
                | (@${csvFile.canonicalPath}) |
        """.trimIndent()

        val specFile = tempDir.resolve("data.spec")
        specFile.writeText(spec)

        val feature = parseContractFileToFeature(specFile)

        val testScenario = feature.generateContractTestScenarios(emptyList()).single()

        val requestPattern = testScenario.httpRequestPattern
        assertThat(requestPattern.multiPartFormDataPattern.single().name).isEqualTo("csv")
        assertThat(requestPattern.multiPartFormDataPattern.single().contentType).isEqualTo("text/csv")

        val generatedValue: MultiPartContentValue =
            requestPattern.multiPartFormDataPattern.single().generate(Resolver()) as MultiPartContentValue
        assertThat(generatedValue.content.toStringLiteral()).isEqualTo(csvFileContent)
    }

    @Nested
    inner class MultiPartRequestBody {
        private val openAPI = """
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data_csv:
                post:
                  summary: "Save data"
                  parameters: []
                  requestBody:
                    content:
                      multipart/form-data:
                        encoding:
                          csv:
                            contentType: text/csv
                        schema:
                          required:
                          -  csv
                          properties:
                            csv:
                              type: "string"
                  responses:
                    200:
                      description: "Get product by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()

        @Test
        fun `should make multipart type optional or non-optional as per the schema`() {
            val openAPINonOptional = """
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data_csv:
                post:
                  summary: "Save data"
                  parameters: []
                  requestBody:
                    content:
                      multipart/form-data:
                        encoding:
                          csv:
                            contentType: text/csv
                        schema:
                          required:
                          -  csv
                          properties:
                            csv:
                              type: "string"
                  responses:
                    200:
                      description: "Get product by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()

            OpenApiSpecification.fromYAML(openAPINonOptional, "").toFeature().let {
                assertThat(it.scenarios.single().httpRequestPattern.multiPartFormDataPattern.single().name).doesNotEndWith(
                    "?"
                )
            }

            val openAPIOptional = """
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data_csv:
                post:
                  summary: "Save data"
                  parameters: []
                  requestBody:
                    content:
                      multipart/form-data:
                        encoding:
                          csv:
                            contentType: text/csv
                        schema:
                          properties:
                            csv:
                              type: "string"
                  responses:
                    200:
                      description: "Get product by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent()

            OpenApiSpecification.fromYAML(openAPIOptional, "").toFeature().let {
                assertThat(it.scenarios.single().httpRequestPattern.multiPartFormDataPattern.single().name).endsWith("?")
            }
        }

        @Test
        fun `support for multipart form data file stub`(@TempDir tempDir: File) {
            val openAPIFile = tempDir.resolve("data.yaml")
            openAPIFile.writeText(openAPI)

            val csvFile = tempDir.resolve("data.csv")
            val csvFileContent = "1,2,3"
            csvFile.writeText(csvFileContent)

            val stubContent = """
            {
              "http-request": {
                "method": "POST",
                "path": "/data_csv",
                "multipart-formdata": [{
                  "name": "csv",
                  "content": "(string)",
                  "contentType": "text/csv"
                }]
              },
              "http-response": {
                "status": 200,
                "body": "success"
              }
            }
        """.trimIndent()

            val stubDir = tempDir.resolve("data_data")
            stubDir.mkdirs()
            val stubFile = stubDir.resolve("stub.json")
            stubFile.writeText(stubContent)

            var testStatus: String

            createStubFromContracts(listOf(openAPIFile.canonicalPath), "localhost", 9000).use {
                testStatus = "test ran"

                val request = HttpRequest(
                    method = "POST",
                    path = "/data_csv",
                    multiPartFormData = listOf(
                        MultiPartFileValue("csv", csvFile.canonicalPath, "text/csv")
                    )
                )

                val response = it.client.execute(request)

                assertThat(response.status).isEqualTo(200)
                assertThat(response.body.toStringLiteral()).isEqualTo("success")
            }

            assertThat(testStatus).isEqualTo("test ran")
        }

        @Disabled
        @Test
        fun `support for multipart form data stub and validate contentType`(@TempDir tempDir: File) {
            val openAPIFile = tempDir.resolve("data.yaml")
            openAPIFile.writeText(openAPI)

            val csvFile = tempDir.resolve("data.csv")
            val csvFileContent = "1,2,3"
            csvFile.writeText(csvFileContent)

            val stubContent = """
            {
              "http-request": {
                "method": "POST",
                "path": "/data_csv",
                "multipart-formdata": [{
                  "name": "csv",
                  "content": "(string)",
                  "contentType": "text/csv"
                }]
              },
              "http-response": {
                "status": 200,
                "body": "success"
              }
            }
        """.trimIndent()

            val stubDir = tempDir.resolve("data_data")
            stubDir.mkdirs()
            val stubFile = stubDir.resolve("stub.json")
            stubFile.writeText(stubContent)

            var testStatus: String

            createStubFromContracts(listOf(openAPIFile.canonicalPath), "localhost", 9000).use {
                testStatus = "test ran"

                val request = HttpRequest(
                    method = "POST",
                    path = "/data_csv",
                    multiPartFormData = listOf(
                        MultiPartFileValue("csv", csvFile.canonicalPath, "text/plain")
                    )
                )

                val response = it.client.execute(request)

                assertThat(response.status).isEqualTo(400)
            }

            assertThat(testStatus).isEqualTo("test ran")
        }

        @Test
        fun `support for multipart form data file stub and validate content`(@TempDir tempDir: File) {
            val openAPIFile = tempDir.resolve("data.yaml")
            openAPIFile.writeText(openAPI)

            val csvFile = tempDir.resolve("data.csv")
            val csvFileContent = "1,2,3"
            csvFile.writeText(csvFileContent)

            val stubContent = """
            {
              "http-request": {
                "method": "POST",
                "path": "/data_csv",
                "multipart-formdata": [
                  {
                    "name": "csv",
                    "content": "1,2,3",
                    "contentType": "text/csv"
                  }
                ]
              },
              "http-response": {
                "status": 200,
                "body": "success"
              }
            }
        """.trimIndent()

            val stubDir = tempDir.resolve("data_data")
            stubDir.mkdirs()
            val stubFile = stubDir.resolve("stub.json")
            stubFile.writeText(stubContent)

            var testStatus: String

            createStubFromContracts(listOf(openAPIFile.canonicalPath), "localhost", 9000).use {
                testStatus = "test ran"

                val request = HttpRequest(
                    method = "POST",
                    path = "/data_csv",
                    multiPartFormData = listOf(
                        MultiPartFileValue("csv", csvFile.canonicalPath, "text/csv")
                    )
                )

                val response = it.client.execute(request)

                assertThat(response.status).isEqualTo(200)
                println(response.body.toStringLiteral())
            }

            assertThat(testStatus).isEqualTo("test ran")
        }

        @Disabled
        @Test
        fun `support for multipart form data non-file stub and validate content type`(@TempDir tempDir: File) {
            val openAPIFile = tempDir.resolve("data.yaml")
            openAPIFile.writeText(openAPI)

            val csvFile = tempDir.resolve("data.csv")
            val csvFileContent = "1,2,3"
            csvFile.writeText(csvFileContent)

            val stubContent = """
            {
              "http-request": {
                "method": "POST",
                "path": "/data_csv",
                "multipart-formdata": [
                  {
                    "name": "csv",
                    "content": "1,2,3",
                    "contentType": "text/csv"
                  }
                ]
              },
              "http-response": {
                "status": 200,
                "body": "success"
              }
            }
        """.trimIndent()

            val stubDir = tempDir.resolve("data_data")
            stubDir.mkdirs()
            val stubFile = stubDir.resolve("stub.json")
            stubFile.writeText(stubContent)

            var testStatus: String

            createStubFromContracts(listOf(openAPIFile.canonicalPath), "localhost", 9000).use {
                testStatus = "test ran"

                val request = HttpRequest(
                    method = "POST",
                    path = "/data_csv",
                    multiPartFormData = listOf(
                        MultiPartContentValue("csv", StringValue("1,2,3"), specifiedContentType = "text/plain")
                    )
                )

                val response = it.client.execute(request)

                assertThat(response.status).isEqualTo(400)
                println(response.body.toStringLiteral())
            }

            assertThat(testStatus).isEqualTo("test ran")
        }

        @Test
        fun `support for multipart form data non-file stub and validate content`(@TempDir tempDir: File) {
            val openAPIFile = tempDir.resolve("data.yaml")
            openAPIFile.writeText(openAPI)

            val csvFile = tempDir.resolve("data.csv")
            val csvFileContent = "1,2,3"
            csvFile.writeText(csvFileContent)

            val stubContent = """
            {
              "http-request": {
                "method": "POST",
                "path": "/data_csv",
                "multipart-formdata": [
                  {
                    "name": "csv",
                    "content": "1,2,3",
                    "contentType": "text/csv"
                  }
                ]
              },
              "http-response": {
                "status": 200,
                "body": "success"
              }
            }
        """.trimIndent()

            val stubDir = tempDir.resolve("data_data")
            stubDir.mkdirs()
            val stubFile = stubDir.resolve("stub.json")
            stubFile.writeText(stubContent)

            var testStatus: String

            createStubFromContracts(listOf(openAPIFile.canonicalPath), "localhost", 9000).use {
                testStatus = "test ran"

                val request = HttpRequest(
                    method = "POST",
                    path = "/data_csv",
                    multiPartFormData = listOf(
                        MultiPartContentValue("csv", StringValue("1,2,3"), specifiedContentType = "text/csv")
                    )
                )

                val response = it.client.execute(request)

                assertThat(response.status).isEqualTo(200)
                println(response.body.toStringLiteral())
            }

            assertThat(testStatus).isEqualTo("test ran")
        }
    }

    @Test
    fun `recognizes null value`() {
        val contractString = """
                openapi: 3.0.3
                info:
                  title: test
                  version: '1.0'
                paths:
                  '/users':
                    post:
                      responses:
                        '200':
                          description: OK
                      requestBody:
                        content:
                          application/json:
                            schema:
                              ${'$'}ref: '#/components/schemas/user'
                components:
                  schemas:
                    user:
                      type: object
                      properties:
                        id:
                          nullable: true
                      required:
                        - id
            """.trimIndent()

        val contract: Feature = OpenApiSpecification.fromYAML(contractString, "").toFeature()

        val scenario = contract.scenarios.single()
        val resolver = scenario.resolver

        val requestPattern = scenario.httpRequestPattern

        val matchingRequest = HttpRequest("POST", "/users", body = parsedJSON("""{"id": null}"""))
        assertThat(requestPattern.matches(matchingRequest, resolver)).isInstanceOf(Result.Success::class.java)

        val nonMatchingRequest = HttpRequest("POST", "/users", body = parsedJSON("""{"id": 10}"""))
        assertThat(requestPattern.matches(nonMatchingRequest, resolver)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should generate tests for inline payload definitions with examples`() {
        val contractString = """
                openapi: 3.0.3
                info:
                  title: test
                  version: '1.0'
                paths:
                  '/users':
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            text/plain:
                              schema:
                                type: string
                              examples:
                                200_OK:
                                  value:
                      requestBody:
                        content:
                          application/json:
                            examples:
                              200_OK:
                                value:
                                    id: abc123
                            schema:
                              type: object
                              properties:
                                id:
                                  type: string
                              required:
                                - id
            """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(contractString, "").toFeature()

        val results: List<Result> = feature.generateContractTestScenarios(emptyList()).map {
            executeTest(it, object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.body).isInstanceOf(JSONObjectValue::class.java)

                    val body = request.body as JSONObjectValue
                    assertThat(body.jsonObject).hasSize(1)
                    assertThat(body.jsonObject).containsEntry("id", StringValue("abc123"))
                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })
        }

        assertThat(results).hasSize(1)

        assertThat(results[0]).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should generate tests for form fields with examples`() {
        val contractString = """
                openapi: 3.0.3
                info:
                  title: test
                  version: '1.0'
                paths:
                  '/users':
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            text/plain:
                              schema:
                                type: string
                              examples:
                                200_OK:
                                  value:
                      requestBody:
                        content:
                          application/x-www-form-urlencoded:
                             examples:
                               200_OK:
                                 value:
                                     Data:
                                       id: abc123
                             encoding:
                               Data:
                                 contentType: application/json
                             schema:
                               type: object
                               properties:
                                 Data:
                                   type: object
                                   properties:
                                     id:
                                       type: string
                                   required:
                                     - id
            """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(contractString, "").toFeature()

        val results: List<Result> = feature.generateContractTestScenarios(emptyList()).map {
            executeTest(it, object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.formFields).containsKey("Data")

                    var parsedValue: Value = JSONObjectValue()
                    assertThatCode { parsedValue = parsedJSON(request.formFields["Data"]!!) }.doesNotThrowAnyException()

                    assertThat((parsedValue as JSONObjectValue).jsonObject).containsEntry("id", StringValue("abc123"))
                    assertThat(request.formFields).hasSize(1)
                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })
        }

        assertThat(results).hasSize(1)
        println(results.single().reportString())

        assertThat(results.single()).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should generate tests for form fields with examples when the fields are in a ref`() {
        val contractString = """
                openapi: 3.0.3
                info:
                  title: test
                  version: '1.0'
                paths:
                  '/users':
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            text/plain:
                              schema:
                                type: string
                              examples:
                                200_OK:
                                  value:
                      requestBody:
                        content:
                          application/x-www-form-urlencoded:
                             examples:
                               200_OK:
                                 value:
                                     Data: abc123
                             schema:
                               ${"$"}ref: '#/components/schemas/Data'
                components:
                  schemas:
                    Data:
                      type: object
                      properties:
                        Data:
                          type: string
            """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(contractString, "").toFeature()

        val results: List<Result> = feature.generateContractTestScenarios(emptyList()).map {
            executeTest(it, object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.formFields).containsKey("Data")
                    assertThat(request.formFields["Data"]).isEqualTo("abc123")
                    assertThat(request.formFields).hasSize(1)

                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })
        }

        assertThat(results).hasSize(1)
        println(results.single().reportString())

        assertThat(results.single()).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should generate tests for multipart fields with examples`() {
        val contractString = """
                openapi: 3.0.3
                info:
                  title: test
                  version: '1.0'
                paths:
                  '/users':
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            text/plain:
                              schema:
                                type: string
                              examples:
                                200_OK:
                                  value:
                      requestBody:
                        content:
                          multipart/form-data:
                             examples:
                               200_OK:
                                 value:
                                   Data: abc123
                             schema:
                               type: object
                               properties:
                                 Data:
                                   type: string
                               required:
                                 - Data
            """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(contractString, "").toFeature()

        val results: List<Result> = feature.generateContractTestScenarios(emptyList()).map {
            executeTest(it, object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.multiPartFormData.first().name).isEqualTo("Data")

                    val content = request.multiPartFormData.first() as MultiPartContentValue
                    assertThat(content.content.toStringLiteral()).isEqualTo("abc123")

                    assertThat(request.multiPartFormData).hasSize(1)
                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })
        }

        assertThat(results).hasSize(1)
        println(results.single().reportString())

        assertThat(results.single()).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `nullable oneOf ref in yaml`() {
        val openAPIText = """
            ---
            openapi: "3.0.1"
            info:
              title: "Test"
              version: "1"
            paths:
              /user:
                post:
                  summary: "Test"
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          required:
                          - "location"
                          properties:
                            location:
                              oneOf:
                              - nullable: true
                              - ${'$'}ref: '#/components/schemas/Address'
                              - ${'$'}ref: '#/components/schemas/LatLong'
                  responses:
                    "200":
                      description: "Test"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            components:
              schemas:
                Address:
                  required:
                  - "street"
                  properties:
                    street:
                      type: "string"
                LatLong:
                  required:
                  - "latitude"
                  - "longitude"
                  properties:
                    latitude:
                      type: "number"
                    longitude:
                      type: "number"
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPIText, "").toFeature()

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "POST",
                    "/user",
                    body = parsedJSON("""{"location": {"street": "Baker Street"}}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "POST",
                    "/user",
                    body = parsedJSON("""{"location": {"latitude": 51.523160, "longitude": -0.158070}}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "POST",
                    "/user",
                    body = parsedJSON("""{"location": null}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")
    }

    @Test
    fun `non-nullable oneOf ref in yaml`() {
        val openAPIText = """
            ---
            openapi: "3.0.1"
            info:
              title: "Test"
              version: "1"
            paths:
              /user:
                post:
                  summary: "Test"
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        schema:
                          required:
                          - "location"
                          properties:
                            location:
                              oneOf:
                              - ${'$'}ref: '#/components/schemas/Address'
                              - ${'$'}ref: '#/components/schemas/LatLong'
                  responses:
                    "200":
                      description: "Test"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            components:
              schemas:
                Address:
                  required:
                  - "street"
                  properties:
                    street:
                      type: "string"
                LatLong:
                  required:
                  - "latitude"
                  - "longitude"
                  properties:
                    latitude:
                      type: "number"
                    longitude:
                      type: "number"
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPIText, "").toFeature()

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "POST",
                    "/user",
                    body = parsedJSON("""{"location": {"street": "Baker Street"}}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "POST",
                    "/user",
                    body = parsedJSON("""{"location": {"latitude": 51.523160, "longitude": -0.158070}}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")

        assertThatThrownBy {
            feature.matchingStub(
                HttpRequest(
                    "POST",
                    "/user",
                    body = parsedJSON("""{"location": null}""")
                ), HttpResponse.ok("success")
            )
        }.satisfies(Consumer { it.instanceOf(NoMatchingScenario::class) })
    }

    // See https://swagger.io/docs/specification/data-models/oneof-anyof-allof-not/#allof
    @Test
    fun `oneOf with discriminator in yaml`() {
        val openAPIText = """
            ---
            openapi: "3.0.1"
            info:
              title: "Test"
              version: "1"
            paths:
              /pets:
                patch:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: "#/components/schemas/Pet_Polymorphic"
                  responses:
                    "200":
                      description: "updated"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            components:
              schemas:
                Pet:
                  type: object
                  required:
                  - pet_type
                  properties:
                    pet_type:
                      type: string
                  discriminator:
                    propertyName: pet_type
                Pet_Polymorphic:
                  oneOf:
                    - ${'$'}ref: '#/components/schemas/Cat'
                    - ${'$'}ref: '#/components/schemas/Dog'
                Dog:
                  allOf:
                  - ${'$'}ref: '#/components/schemas/Pet'
                  - type: object
                    properties:
                      bark:
                        type: boolean
                      breed:
                        type: string
                        enum: [Dingo, Husky]
                Cat:
                  allOf:
                  - ${'$'}ref: '#/components/schemas/Pet'
                  - type: object
                    properties:
                      hunts:
                        type: boolean
                      age:
                        type: integer
        """.trimIndent()
        val feature = OpenApiSpecification.fromYAML(openAPIText, "").toFeature()

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "PATCH",
                    "/pets",
                    body = parsedJSON("""{"pet_type": "Cat", "age": 3}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "PATCH",
                    "/pets",
                    body = parsedJSON("""{"pet_type": "Dog", "bark": true}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")

        assertThat(
            feature.matchingStub(
                HttpRequest(
                    "PATCH",
                    "/pets",
                    body = parsedJSON("""{"pet_type": "Dog", "bark": false, "breed": "Dingo"}""")
                ), HttpResponse.ok("success")
            ).response.headers["X-Specmatic-Result"]
        ).isEqualTo("success")

        assertThatThrownBy {
            feature.matchingStub(
                HttpRequest(
                    "PATCH",
                    "/pets",
                    body = parsedJSON("""{"age": 3}""")
                ), HttpResponse.ok("success")
            )
        }.isInstanceOf(NoMatchingScenario::class.java)

        assertThatThrownBy {
            feature.matchingStub(
                HttpRequest(
                    "PATCH",
                    "/pets",
                    body = parsedJSON("""{"pet_type": "Cat", "bark": true}""")
                ), HttpResponse.ok("success")
            )
        }.isInstanceOf(NoMatchingScenario::class.java)
    }

    @Test
    fun `should read WIP tag in OpenAPI paths`() {
        val contractString = """
                openapi: 3.0.3
                info:
                  title: test
                  version: '1.0'
                paths:
                  '/users':
                    post:
                      tags:
                        - WIP
                      responses:
                        '200':
                          description: OK
                          content:
                            text/plain:
                              schema:
                                type: string
                      requestBody:
                        content:
                          application/json:
                            schema:
                              type: object
                              properties:
                                id:
                                  type: string
                              required:
                                - id
            """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(contractString, "").toFeature()

        assertThat(feature.scenarios.first().ignoreFailure).isTrue()
    }

    @Test
    fun `should not break when there are no tags in OpenAPI paths`() {
        val contractString = """
                openapi: 3.0.3
                info:
                  title: test
                  version: '1.0'
                paths:
                  '/users':
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            text/plain:
                              schema:
                                type: string
                      requestBody:
                        content:
                          application/json:
                            schema:
                              type: object
                              properties:
                                id:
                                  type: string
                              required:
                                - id
            """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(contractString, "").toFeature()

        assertThat(feature.scenarios.first().ignoreFailure).isFalse()
    }

    @Test
    fun `should be able to accept expectations when examples are provided and should not hardcode the request to the specific examples`() {
        val contractString = """
                openapi: 3.0.3
                info:
                  title: test
                  version: '1.0'
                paths:
                  '/users':
                    post:
                      responses:
                        '200':
                          description: OK
                          content:
                            text/plain:
                              schema:
                                type: string
                              examples:
                                200_OK:
                                  value: success
                      requestBody:
                        content:
                          application/json:
                            examples:
                                200_OK:
                                  value:
                                    id: "abc123"
                            schema:
                              type: object
                              properties:
                                id:
                                  type: string
                              required:
                                - id
            """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(contractString, "").toFeature()
        val match: List<Pair<Scenario, Result>> = feature.compatibilityLookup(
            HttpRequest(
                "POST",
                "/users",
                body = parsedJSONObject("""{"id": "xyz789"}""")
            )
        )

        val result = match.single().second
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `two APIs with IDs in the URL should be merged into one API`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: API 1
              Given type RequestBody
              | hello | (string) |
              When POST /data/10
              And request-body (RequestBody)
              Then status 200

            Scenario: API 2
              Given type RequestBody
              | hello | (string) |
              When POST /data/20
              And request-body (RequestBody)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data/30",
                        body = parsedJSON("""{"hello": "Jill"}""")
                    ), HttpResponse.OK
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
            """
            ---
            openapi: 3.0.1
            info:
              title: API
              version: 1
            paths:
              /data/{id}:
                post:
                  summary: API 1
                  parameters:
                  - name: id
                    in: path
                    required: true
                    schema:
                      type: number
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/1_RequestBody'
                  responses:
                    200:
                      description: API 1
            components:
              schemas:
                1_RequestBody:
                  required:
                  - hello
                  properties:
                    hello:
                      type: string
              """.trimIndent()
        )
    }

    @Test
    fun `a single API with an ID in the URL should be turn into a URL matcher with an id path param`() {
        val feature = parseGherkinStringToFeature(
            """
            Feature: API
            
            Scenario: API 1
              Given type RequestBody
              | hello | (string) |
              When POST /data/10
              And request-body (RequestBody)
              Then status 200
            """.trimIndent()
        )
        val openAPI = feature.toOpenApi()

        with(OpenApiSpecification("/file.yaml", openAPI).toFeature()) {
            assertThat(
                this.matches(
                    HttpRequest(
                        "POST",
                        "/data/30",
                        body = parsedJSON("""{"hello": "Jill"}""")
                    ), HttpResponse.OK
                )
            ).isTrue
        }

        val openAPIYaml = openAPIToString(openAPI)
        portableComparisonAcrossBuildEnvironments(
            openAPIYaml,
            """
            ---
            openapi: 3.0.1
            info:
              title: API
              version: 1
            paths:
              /data/{id}:
                post:
                  summary: API 1
                  parameters:
                  - name: id
                    in: path
                    required: true
                    schema:
                      type: number
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/1_RequestBody'
                  responses:
                    200:
                      description: API 1
            components:
              schemas:
                1_RequestBody:
                  required:
                  - hello
                  properties:
                    hello:
                      type: string
              """.trimIndent()
        )
    }

    @Test
    fun `should log messages from the parser when parsing fails`() {
        val contractHasBadDescriptionInResponseSchema = """
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
            text/plain:
              schema:
              description: Says hello
                type: string
        """.trimIndent()

        val testLogger = object : LogStrategy {
            val messages = mutableListOf<String>()
            override val printer: CompositePrinter
                get() = TODO("Not yet implemented")

            override fun keepReady(msg: LogMessage) {
                TODO("Not yet implemented")
            }

            override fun exceptionString(e: Throwable, msg: String?): String {
                TODO("Not yet implemented")
            }

            override fun ofTheException(e: Throwable, msg: String?): LogMessage {
                TODO("Not yet implemented")
            }

            override fun log(e: Throwable, msg: String?) {
                TODO("Not yet implemented")
            }

            override fun log(msg: String) {
                messages.add(msg)
            }

            override fun log(msg: LogMessage) {
                TODO("Not yet implemented")
            }

            override fun newLine() {
                TODO("Not yet implemented")
            }

            override fun debug(msg: String): String {
                TODO("Not yet implemented")
            }

            override fun debug(msg: LogMessage) {
                TODO("Not yet implemented")
            }

            override fun debug(e: Throwable, msg: String?) {
                TODO("Not yet implemented")
            }

        }

        ignoreButLogException {
            OpenApiSpecification.fromYAML(contractHasBadDescriptionInResponseSchema, "", testLogger)
        }

        assertThat(testLogger.messages).isNotEmpty
    }

    @Test
    fun `should log messages from the parser when parsing does not fail`() {
        val contractHasBadDescriptionInResponseSchema = """
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
  /data:
    post:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      requestBody:
        content:
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
        """.trimIndent()

        val testLogger = object : LogStrategy {
            val messages = mutableListOf<String>()
            override val printer: CompositePrinter
                get() = TODO("Not yet implemented")

            override fun keepReady(msg: LogMessage) {
                TODO("Not yet implemented")
            }

            override fun exceptionString(e: Throwable, msg: String?): String {
                TODO("Not yet implemented")
            }

            override fun ofTheException(e: Throwable, msg: String?): LogMessage {
                TODO("Not yet implemented")
            }

            override fun log(e: Throwable, msg: String?) {
                TODO("Not yet implemented")
            }

            override fun log(msg: String) {
                println(msg)
                messages.add(msg)
            }

            override fun log(msg: LogMessage) {
                TODO("Not yet implemented")
            }

            override fun newLine() {
                TODO("Not yet implemented")
            }

            override fun debug(msg: String): String {
                TODO("Not yet implemented")
            }

            override fun debug(msg: LogMessage) {
                TODO("Not yet implemented")
            }

            override fun debug(e: Throwable, msg: String?) {
                TODO("Not yet implemented")
            }

        }

        ignoreButLogException {
            OpenApiSpecification.fromYAML(contractHasBadDescriptionInResponseSchema, "", testLogger)
        }

        assertThat(testLogger.messages).isNotEmpty
    }

    @Test
    fun `nullable empty object should translate to null when found in oneOf`() {
        val contract = OpenApiSpecification.fromYAML(
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
                  oneOf:
                    - properties: {}
                      nullable: true
                    - type: string
      responses:
        200:
          description: "Add person by id"
          content:
            text/plain:
              schema:
                type: "string"
""".trimIndent(), ""
        ).toFeature()

        val requestBodyType = contract.scenarios.first().httpRequestPattern.body as JSONObjectPattern
        val addressType = requestBodyType.pattern["address"] as AnyPattern

        assertThat(addressType.pattern).hasSize(2)
        assertThat(NullPattern).isIn(addressType.pattern)
        assertThat(StringPattern()).isIn(addressType.pattern)
    }

    @Test
    fun `should be possible to have two stubs of authorization header with different values`() {
        val specification = """
            openapi: 3.0.1
            info:
              title: New Feature
              version: "1"
            paths:
              /test:
                post:
                  summary: auth
                  parameters:
                    - in: header
                      name: Authorization
                      schema:
                        type: string
                      required: true
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/Request'
                  responses:
                    "200":
                      description: New scenario
                      content:
                        text/plain:
                          schema:
                            type: string
                    "400":
                      description: New scenario
                      content:
                        text/plain:
                          schema:
                            type: string
            components:
              schemas:
                Request:
                  type: object
                  required:
                    - item
                  properties:
                    item:
                      type: string

        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(specification, "/file.yaml").toFeature()

        val validAuthStub: ScenarioStub = ScenarioStub(
            HttpRequest(
                "POST",
                "/test",
                mapOf("Authorization" to "valid"),
                body = parsedJSONObject("""{"item": "data"}""")
            ),
            HttpResponse.ok("success")
        )

        val invalidAuthStub: ScenarioStub = ScenarioStub(
            HttpRequest(
                "POST",
                "/test",
                mapOf("Authorization" to "invalid"),
                body = parsedJSONObject("""{"item": "data"}""")
            ),
            HttpResponse(400, "failed")
        )

        HttpStub(feature, listOf(invalidAuthStub, validAuthStub)).use { stub ->
            val request = HttpRequest(
                "POST",
                "/test",
                body = parsedJSONObject("""{"item": "data"}""")
            )

            with(stub.client.execute(request.copy(headers = mapOf("Authorization" to "valid")))) {
                assertThat(this.body.toStringLiteral()).isEqualTo("success")
                assertThat(this.status).isEqualTo(200)
            }

            with(stub.client.execute(request.copy(headers = mapOf("Authorization" to "invalid")))) {
                assertThat(this.body.toStringLiteral()).isEqualTo("failed")
                assertThat(this.status).isEqualTo(400)
            }
        }
    }

    @Test
    fun `validate the second element in a list`() {
        val feature = OpenApiSpecification.fromYAML(
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
                          type: array
                          items:
                            type: object
                            required:
                              - id
                              - name
                            properties:
                              id:
                                type: string
                              name:
                                type: string
                  responses:
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            components:
              schemas: {}
        """.trimIndent(), ""
        ).toFeature()

        with(feature) {
            val result =
                this.scenarios.first().matchesMock(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""[{"id": "123", "name": "Jack Sprat"}, {"id": "456"}]""")
                    ), HttpResponse.ok("success")
                )

            assertThat(result.reportString()).isEqualTo(
                """
                >> REQUEST.BODY[1].name

                   Expected key named "name" was missing
            """.trimIndent()
            )
        }
    }

    @Test
    fun `validate a nullable array`() {
        val feature = OpenApiSpecification.fromYAML(
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
                          type: array
                          items:
                            type: object
                            required:
                              - id
                              - names
                            properties:
                              id:
                                type: string
                              names:
                                type: array
                                nullable: true
                                items:
                                  type: string
                  responses:
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
            components:
              schemas: {}
        """.trimIndent(), ""
        ).toFeature()

        with(feature) {
            val result =
                this.scenarios.first().matchesMock(
                    HttpRequest(
                        "POST",
                        "/person",
                        body = parsedJSON("""[{"id": "123", "names": ["Jack", "Sprat"]}, {"id": "456", "names": null}]""")
                    ), HttpResponse.ok("success")
                )

            assertThat(result).isInstanceOf(Result.Success::class.java)
        }
    }

    @Test
    fun `randomized response when stubbing out API with byte array request body`() {
        val specification = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Add data"
                  requestBody:
                    content:
                      application/octet-stream:
                        schema:
                          type: string
                          format: byte
                  responses:
                    200:
                      description: "Result"
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        HttpStub(specification).use { stub ->
            val base64EncodedRequestBody = Base64.getEncoder().encodeToString("hello world".encodeToByteArray())

            val response = stub.client.execute(
                HttpRequest(
                    method = "POST",
                    path = "/data",
                    body = StringValue(base64EncodedRequestBody)
                )
            )

            assertThat(response.status).isEqualTo(200)
        }
    }

    @Test
    fun `stubbed response when stubbing out API with byte array request body`() {
        val specification = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Add data"
                  requestBody:
                    content:
                      application/octet-stream:
                        schema:
                          type: string
                          format: byte
                  responses:
                    200:
                      description: "Result"
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        HttpStub(specification).use { stub ->
            val base64EncodedRequestBody = Base64.getEncoder().encodeToString("hello world".encodeToByteArray())

            val stubbedRequest = HttpRequest(
                method = "POST",
                path = "/data",
                body = StringValue(base64EncodedRequestBody)
            )

            stub.client.execute(
                HttpRequest(
                    method = "POST",
                    path = "/_specmatic/expectations",
                    body = StringValue("""
                        {
                            "http-request": {
                                "method": "POST",
                                "path": "/data",
                                "body": "$base64EncodedRequestBody"
                            },
                            "http-response": {
                                "status": 200,
                                "body": "success"
                            }
                        }
                    """.trimIndent())
                )
            ).also { response ->
                assertThat(response.status).isEqualTo(200)
            }

            val response = stub.client.execute(stubbedRequest)
            assertThat(response.status).withFailMessage("Got a non-200 status which means that the stub did not respond to the request").isEqualTo(200)
            assertThat(response.body.toStringLiteral()).withFailMessage("Did not get success, most likely got a random response, which means that the stubbed response was not returned").isEqualTo("success")
        }
    }

    @Test
    fun `cannot stub out non-base64 request for a byte array request body`() {
        val specification = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Add data"
                  requestBody:
                    content:
                      application/octet-stream:
                        schema:
                          type: string
                          format: byte
                  responses:
                    200:
                      description: "Result"
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        HttpStub(specification).use { stub ->
            val vanillaNonBase64Request = "]"

            stub.client.execute(
                HttpRequest(
                    method = "POST",
                    path = "/_specmatic/expectations",
                    body = StringValue("""
                        {
                            "http-request": {
                                "method": "POST",
                                "path": "/data",
                                "body": "$vanillaNonBase64Request"
                            },
                            "http-response": {
                                "status": 200,
                                "body": "success"
                            }
                        }
                    """.trimIndent())
                )
            ).also { response ->
                assertThat(response.status).isEqualTo(400)
            }
        }
    }

    @Test
    fun `test request of type byte array request body sends a random base64 request value`() {
        val specification = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Add data"
                  requestBody:
                    content:
                      application/octet-stream:
                        schema:
                          type: string
                          format: byte
                  responses:
                    200:
                      description: "Result"
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        val results = specification.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.body.toStringLiteral()).isBase64()
                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `test request of type byte array request body loads sends a base64 example`() {
        val base64EncodedRequestBody = Base64.getEncoder().encodeToString("hello world".encodeToByteArray())

        val specification = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Add data"
                  requestBody:
                    content:
                      application/octet-stream:
                        schema:
                          type: string
                          format: byte
                        examples:
                          SUCCESS:
                            value: $base64EncodedRequestBody
                  responses:
                    200:
                      description: "Result"
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            SUCCESS:
                              value: success
        """.trimIndent(), "").toFeature()

        val results = specification.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val expectedRequestBody = "hello world".toByteArray()
                val actualRequestBody = request.body.toStringLiteral()

                assertThat(actualRequestBody).asBase64Decoded().isEqualTo(expectedRequestBody)

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    @Disabled
    fun `byte string request backward compatibility check`() {
        // TODO: backward compatibility check for byte arrays to string and vice versa is not working
        val byteString = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Add data"
                  requestBody:
                    content:
                      application/octet-stream:
                        schema:
                          type: string
                          format: byte
                  responses:
                    200:
                      description: "Result"
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        val normalString = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Data API"
              version: "1"
            paths:
              /data:
                post:
                  summary: "Add data"
                  requestBody:
                    content:
                      application/octet-stream:
                        schema:
                          type: string
                  responses:
                    200:
                      description: "Result"
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        testBackwardCompatibility(normalString, byteString).also { result ->
            assertThat(result.hasFailures()).isTrue()
        }

        testBackwardCompatibility(normalString, byteString).also { result ->
            assertThat(result.hasFailures()).isTrue()
        }
    }

    @Test
    fun `support for multipart form data part array with a return type byte array string`() {
        val openAPI = """
                openapi: 3.0.3
                info:
                  title: Return type of multipart-form-data with string format byte
                  description: Service to add a test case to a Specmatic feature
                  version: 1.0.0
                tags:
                  - name: UploadFile
                paths:
                  "/file":
                    post:
                      tags:
                        - UploadFile
                      operationId: sendMessage
                      requestBody:
                        content:
                          multipart/form-data:
                            schema:
                              type: object
                              properties:
                                filesPart:
                                  type: string
                                  format: binary
                              required:
                                - filesPart
                      responses:
                        "200":
                          description: "Send Message Response"
                          content:
                            multipart/form-data:
                              schema:
                                type: object
                                properties:
                                  filename:
                                    type: string
                                    format: byte
            """.trimIndent()

        val specifications = OpenApiSpecification.fromYAML(openAPI, "").toScenarioInfos()
        assertTrue(specifications.first.isNotEmpty())
        with(OpenApiSpecification.fromYAML(openAPI, "",).toFeature()) {
            val result =
                    this.scenarios.first().matchesMock(
                            HttpRequest(
                                    "POST",
                                    "/file",
                                    multiPartFormData = listOf(MultiPartFileValue("filesPart", "test.pdf", "application/pdf", "UTF-8"))
                            ), HttpResponse.ok("{\"filename\": \"ThIsi5ByT3sD4tA\"}")
                    )
            assertThat(result).isInstanceOf(Result.Success::class.java)
        }
    }

    private fun ignoreButLogException(function: () -> OpenApiSpecification) {
        try {
            function()
        } catch (e: Throwable) {
            println(exceptionCauseMessage(e))
        }
    }
}

