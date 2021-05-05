package `in`.specmatic.conversions

import `in`.specmatic.stub.HttpStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import java.io.File
import java.net.URI

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

        val response= HttpStub(feature).use { mock ->
            val restTemplate = RestTemplate()
            restTemplate.exchange(URI.create("http://localhost:9000/hello"), HttpMethod.GET, null, String::class.java)
        }

        assertThat(response.statusCodeValue).isEqualTo(200)

        openApiFile.delete()
    }
}