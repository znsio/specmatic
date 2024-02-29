package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.QueryParameters
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.stub.HttpStubData
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FreeFormQueryParams {
    @Test
    fun `support query parameter as free-form dictionary`() {
        val openAPI = """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    get:
      parameters:
      - name: userGroups
        in: query
        schema:
          type: object
          additionalProperties:
            type: string
      responses:
        200:
          description: API
""".trimIndent()

        println(openAPI)
        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request = HttpRequest("GET", "/data", queryParams = QueryParameters(mapOf("key1" to "myEnumVal1", "key2" to "myEnumVal2")))
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        println(stub.requestType)

        Assertions.assertThat(stub.requestType.method).isEqualTo("GET")
        Assertions.assertThat(stub.response.status).isEqualTo(200)
    }

    @Test
    fun `support query parameter as free-form dictionary having enum values`() {
        val openAPI = """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    get:
      parameters:
      - name: userGroups
        in: query
        schema:
          type: object
          additionalProperties:
            type: string
            enum:
            - myEnumVal1
            - myEnumVal2
      responses:
        200:
          description: API
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val request = HttpRequest("GET", "/data", queryParams = QueryParameters(mapOf("key1" to "myEnumVal1", "key2" to "myEnumVal2")))
        val response = HttpResponse.OK

        val stub: HttpStubData = feature.matchingStub(request, response)

        Assertions.assertThat(stub.requestType.method).isEqualTo("GET")
        Assertions.assertThat(stub.response.status).isEqualTo(200)
    }


    @Test
    fun `support stubbing out query params as free-form dictionary`() {
        val openAPI = """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    get:
      parameters:
      - name: userGroups
        in: query
        schema:
          type: object
          additionalProperties:
            type: integer
      responses:
        200:
          description: API
          content:
            application/json:
              schema:
                type: string
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()


        HttpStub(feature).use { stub ->
            val expectation = ScenarioStub(
                HttpRequest("GET", "/data", queryParams = QueryParameters(mapOf("key1" to "10", "key2" to "20"))),
                HttpResponse.ok("success1")
            )

            stub.client.execute(
                HttpRequest("POST", "/_specmatic/expectations", body = expectation.toJSON())
            ).let { response ->
                assertThat(response.status).isEqualTo(200)
            }

            stub.client.execute(expectation.request).let { response ->
                assertThat(response.body.toStringLiteral()).isEqualTo("success1")
            }
        }
    }

    @Test
    fun `support stubbing out query params as free-form dictionary with two different sets of params`() {
        val openAPI = """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    get:
      parameters:
      - name: userGroups
        in: query
        schema:
          type: object
          additionalProperties:
            type: integer
      responses:
        200:
          description: API
          content:
            application/json:
              schema:
                type: string
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        val expectations = listOf(
            ScenarioStub(
                HttpRequest("GET", "/data", queryParams = QueryParameters(mapOf("key1" to "10", "key2" to "20"))),
                HttpResponse.ok("success1")
            ),
            ScenarioStub(
                HttpRequest("GET", "/data", queryParams = QueryParameters(mapOf("key1" to "40", "key2" to "30"))),
                HttpResponse.ok("success2")
            )
        )

        HttpStub(feature).use { stub ->
            expectations.forEach { expectation ->
                stub.client.execute(HttpRequest("POST", "/_specmatic/expectations", body = expectation.toJSON())).let { response ->
                    assertThat(response.status).isEqualTo(200)
                }
            }

            expectations.forEach { (request, expectedResponse) ->
                stub.client.execute(request).let { response ->
                    assertThat(response.body).isEqualTo(expectedResponse.body)
                }
            }
        }
    }

    @Test
    fun `attempt to stub out query parameter as free-form dictionary with wrong value type should fail`() {
        val openAPI = """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    get:
      parameters:
      - name: userGroups
        in: query
        schema:
          type: object
          additionalProperties:
            type: integer
      responses:
        200:
          description: API
          content:
            application/json:
              schema:
                type: string
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        HttpStub(feature).use { stub ->
            val expectation = ScenarioStub(
                HttpRequest("GET", "/data", queryParams = QueryParameters(mapOf("key1" to "test"))),
                HttpResponse.ok("success1")
            )

            val response =
                stub.client.execute(HttpRequest("POST", "/_specmatic/expectations", body = expectation.toJSON()))

            assertThat(response.status).isEqualTo(400)

            val responseBody = response.body.toStringLiteral()

            assertThat(responseBody).withFailMessage(responseBody).contains("key1")
        }
    }

    @Test
    fun `support query parameter with additionalProperties set to false`() {
        val openAPI = """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    get:
      parameters:
      - name: userGroups
        in: query
        schema:
          type: object
          additionalProperties: false
      responses:
        200:
          description: API
""".trimIndent()

        val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()

        HttpStub(feature).use { stub ->
            val request = HttpRequest("GET", "/data", queryParams = QueryParameters(mapOf("key1" to "abc", "key2" to "def")))

            val expectation = ScenarioStub(request, HttpResponse.OK)

            val response =
                stub.client.execute(HttpRequest("POST", "/_specmatic/expectations", body = expectation.toJSON()))

            assertThat(response.status).isEqualTo(400)

            val responseBody = response.body.toStringLiteral()

            assertThat(responseBody).withFailMessage(responseBody).contains("key1")
            assertThat(responseBody).withFailMessage(responseBody).contains("key2")
        }
    }
}