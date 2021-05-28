package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.test.TestExecutor
import com.fasterxml.jackson.annotation.JsonProperty
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.io.File
import java.net.URI

internal class OpenApiKtTest {
    companion object {
        const val OPENAPI_FILE = "openApiTest.yaml"
        const val OPENAPI_FILE_WITH_EXAMPLES = "openApiWithExamples.yaml"

        val openAPISpec = """
Feature: Hello world

Background:
  Given openapi openApiTest.yaml            

Scenario: zero should return not found
  When GET /hello/0
  Then status 404
  And response-header Content-Type application/json
        """.trimIndent()

        val gherkinScenarioWithPathParameterDataTypeThatDoesNotMatchOpenAPI = """
Feature: Hello world

Background:
  Given openapi openApiTest.yaml            

Scenario: sending string instead of number should return not found
  When GET /hello/test
  Then status 404
  And response-header Content-Type application/json
        """.trimIndent()

        val gherkinScenarioWithResponseCodeNotDefinedInOpenAPI = """
Feature: Hello world

Background:
  Given openapi openApiTest.yaml            

Scenario: zero should return forbidden
  When GET /hello/0
  Then status 403
  And response-header Content-Type application/json
        """.trimIndent()
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
    """.trim()

        val openApiFile = File(OPENAPI_FILE)
        openApiFile.createNewFile()
        openApiFile.writeText(openAPI)

        val openAPIWithExamples = """
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
          examples:
            200_OKAY:
              value: 15
              summary: value that returns 200
            404_NOT_FOUND:
              value: 0
              summary: value that returns 404
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
              examples:
                200_OKAY:
                  value: hello15
                  summary: response that matches 200_OKAY
        '404':
          description: Not Found
          content:
            application/json:
              schema:
                type: string
              examples:
                404_NOT_FOUND:
                  value: zero not found
                  summary: response that matches 404_NOT_FOUND
        '400':
          description: Bad Request
          content:
            application/json:
              schema:
                type: string
    """.trim()

        val openApiFileWithExamples = File(OPENAPI_FILE_WITH_EXAMPLES)
        openApiFileWithExamples.createNewFile()
        openApiFileWithExamples.writeText(openAPIWithExamples)
    }

    @AfterEach
    fun `teardown`() {
        File(OPENAPI_FILE).delete()
        File(OPENAPI_FILE_WITH_EXAMPLES).delete()
    }

    @Test
    fun `should create stub from gherkin that includes OpenAPI spec`() {
        val feature = parseGherkinStringToFeature(openAPISpec)

        val response = HttpStub(feature).use { mock ->
            val restTemplate = RestTemplate()
            restTemplate.exchange(URI.create("http://localhost:9000/hello/1"), HttpMethod.GET, null, String::class.java)
        }

        assertThat(response.statusCodeValue).isEqualTo(200)

        HttpStub(feature).use { mock ->
            val restTemplate = RestTemplate()
            try {
                restTemplate.exchange(
                    URI.create("http://localhost:9000/hello/0"),
                    HttpMethod.GET,
                    null,
                    String::class.java
                )
            } catch (e: HttpClientErrorException) {
                assertThat(e.statusCode).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND)
            }
        }
    }

    @Test
    fun `should create test from gherkin that includes OpenAPI spec`() {
        val flags = mutableMapOf<String, Boolean>()

        val feature = parseGherkinStringToFeature(openAPISpec)

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    flags["${request.path} executed"] = true
                    assertThat(request.path).matches("""\/hello\/[0-9]+""")
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val id = request.path!!.split('/')[2].toInt()
                    val status = when (id) {
                        0 -> 404
                        else -> 200
                    }
                    return HttpResponse(status, "", headers)
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertThat(flags["/hello/0 executed"]).isTrue
        assertThat(flags.size).isEqualTo(2)
        assertTrue(results.success(), results.report())
    }

    @Test
    fun `should create tests from OpenAPI examples`() {
        val flags = mutableMapOf<String, Boolean>()

        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openApiWithExamples.yaml
        """.trimIndent()
        )

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    flags["${request.path} executed"] = true
                    assertThat(request.path).matches("""\/hello\/[0-9]+""")
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val id = request.path!!.split('/')[2].toInt()
                    val status = when (id) {
                        0 -> 404
                        else -> 200
                    }
                    return HttpResponse(status, "hello world", headers)
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertThat(flags["/hello/0 executed"]).isTrue
        assertThat(flags["/hello/15 executed"]).isTrue
        assertThat(flags.size).isEqualTo(3)
        assertTrue(results.success(), results.report())
    }

    @Test
    fun `should report error in test with both OpenAPI and Gherkin scenario names`() {
        val flags = mutableMapOf<String, Boolean>()

        val feature = parseGherkinStringToFeature(openAPISpec)

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    flags["executed"] = true
                    assertThat(request.path).matches("""\/hello\/[0-9]+""")
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val id = request.path!!.split('/')[2].toInt()
                    val status = when (id) {
                        0 -> 403
                        else -> 202
                    }
                    return HttpResponse(status, "", headers)
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertThat(flags["executed"]).isTrue
        assertFalse(results.success())
        assertThat(results.report()).isEqualTo(
            """
            In scenario "zero should return not found"
            >> RESPONSE.STATUS

            Expected status: 404, actual: 403

            In scenario "Request: hello world Response: Says hello"
            >> RESPONSE.STATUS

            Expected status: 200, actual: 202
        """.trimIndent()
        )
    }

    @Test
    fun `should throw error when request in Gherkin scenario does not match included OpenAPI spec`() {
        assertThatThrownBy {
            parseGherkinStringToFeature(gherkinScenarioWithPathParameterDataTypeThatDoesNotMatchOpenAPI)
        }.satisfies {
            assertThat(it.message).isEqualTo("""Scenario: "sending string instead of number should return not found" request is not as per included wsdl / OpenApi spec""")
        }
    }

    @Test
    fun `should throw error when response code in Gherkin scenario does not match included OpenAPI spec`() {
        assertThatThrownBy {
            parseGherkinStringToFeature(gherkinScenarioWithResponseCodeNotDefinedInOpenAPI)
        }.satisfies {
            assertThat(it.message).isEqualTo("""Scenario: "zero should return forbidden" response is not as per included wsdl / OpenApi spec""")
        }
    }

    @Test
    fun `should generate stub with non primitive open api data types`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent()
        )

        val response = HttpStub(feature).use { mock ->
            val restTemplate = RestTemplate()
            restTemplate.exchange(URI.create("http://localhost:9000/pets/1"), HttpMethod.GET, null, Pet::class.java)
        }

        assertThat(response.statusCodeValue).isEqualTo(200)
        assertThat(response.body).isInstanceOf(Pet::class.java)
    }
}

data class Pet(
    @JsonProperty("name") val name: String,
    @JsonProperty("tag") val tag: String,
    @JsonProperty("id") val id: Int
)
