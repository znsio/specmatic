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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.net.URI

internal class OpenApiKtTest {
    companion object {
        val openAPISpec = """
Feature: Hello world

Background:
  Given openapi openapi/hello.yaml            

Scenario: zero should return not found
  When GET /hello/0
  Then status 404
  And response-header Content-Type application/json
        """.trimIndent()

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
  Given openapi openapi/helloWithExamples.yaml
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
            parseGherkinStringToFeature(
                """
        Feature: Hello world
        
        Background:
          Given openapi openapi/hello.yaml            
        
        Scenario: sending string instead of number should return not found
          When GET /hello/test
          Then status 404
          And response-header Content-Type application/json
                """.trimIndent()
            )
        }.satisfies {
            assertThat(it.message).isEqualTo("""Scenario: "sending string instead of number should return not found" request is not as per included wsdl / OpenApi spec""")
        }
    }

    @Test
    fun `should throw error when response code in Gherkin scenario does not match included OpenAPI spec`() {
        assertThatThrownBy {
            parseGherkinStringToFeature(
                """
        Feature: Hello world
        
        Background:
          Given openapi openapi/hello.yaml            
        
        Scenario: zero should return forbidden
          When GET /hello/0
          Then status 403
          And response-header Content-Type application/json
                """.trimIndent()
            )
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
