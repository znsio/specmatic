package `in`.specmatic.conversions

import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import java.io.File
import java.net.URI
import `in`.specmatic.core.*
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.assertTrue

internal class OpenApiKtTest {
    @Test
    fun `should create stub from OpenAPI spec`() {
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
  /hello:
    get:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
    """.trim()

        val openApiFile = File("openApiTest.yaml")
        openApiFile.createNewFile()
        openApiFile.writeText(openAPI)

        val feature = toFeatures(openApiFile.absolutePath)

        val response = HttpStub(feature).use { mock ->
            val restTemplate = RestTemplate()
            restTemplate.exchange(URI.create("http://localhost:9000/hello"), HttpMethod.GET, null, String::class.java)
        }

        assertThat(response.statusCodeValue).isEqualTo(200)

        openApiFile.delete()
    }

    @Test
    fun `should create test from OpenAPI spec`() {
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
  /hello:
    get:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
    """.trim()

        val openApiFile = File("openApiTest.yaml")
        openApiFile.createNewFile()
        openApiFile.writeText(openAPI)

        val flags = mutableMapOf<String, Boolean>()

        val feature = toFeatures(openApiFile.absolutePath)

        val results = feature[0].executeTests(
                object : TestExecutor {
                    override fun execute(request: HttpRequest): HttpResponse {
                        flags["executed"] = true
                        Assertions.assertEquals("/hello", request.path)
                        val headers: HashMap<String, String> = object : HashMap<String, String>() {
                            init {
                                put("Content-Type", "application/json")
                            }
                        }
                        return HttpResponse(200, "", headers)
                    }

                    override fun setServerState(serverState: Map<String, Value>) {
                    }
                }
        )

        assertThat(flags["executed"]).isTrue
        assertTrue(results.success(), results.report())

        openApiFile.delete()
    }

    @Test
    fun `should report error on test from OpenAPI spec with scenario name`() {
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
  /hello:
    get:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
    """.trim()

        val openApiFile = File("openApiTest.yaml")
        openApiFile.createNewFile()
        openApiFile.writeText(openAPI)

        val flags = mutableMapOf<String, Boolean>()

        val feature = toFeatures(openApiFile.absolutePath)

        val results = feature[0].executeTests(
                object : TestExecutor {
                    override fun execute(request: HttpRequest): HttpResponse {
                        flags["executed"] = true
                        Assertions.assertEquals("/hello", request.path)
                        val headers: HashMap<String, String> = object : HashMap<String, String>() {
                            init {
                                put("Content-Type", "application/json")
                            }
                        }
                        return HttpResponse(400, "Error", headers)
                    }

                    override fun setServerState(serverState: Map<String, Value>) {
                    }
                }
        )

        assertThat(flags["executed"]).isTrue
        assertFalse(results.success())
        assertThat(results.report()).isEqualTo("""
            In scenario "Request: hello world Response: Says hello"
            >> RESPONSE.STATUS

            Expected status: 200, actual: 400
        """.trimIndent())

        openApiFile.delete()
    }
}