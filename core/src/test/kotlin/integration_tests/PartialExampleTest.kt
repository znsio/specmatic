package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.stub.HttpStub
import io.specmatic.stub.captureStandardOutput
import io.specmatic.stub.createStubFromContracts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PartialExampleTest {
    @Test
    fun `stub should load and match a partial example with concrete values in request and response bodies`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_in_response_body.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("POST", "/person", body = parsedJSONObject("""{"name": "Stan", "department": "engineering"}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.findFirstChildByPath("location")?.toStringLiteral()).isEqualTo("Mumbai")
        }
    }

    @Test
    fun `stub should use a partial example with template params in request and response`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_and_substitution_in_response_body.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("POST", "/person", body = parsedJSONObject("""{"name": "Stan", "department": "engineering"}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue

            assertThat(responseBody.jsonObject).containsKeys("id")
            assertThat(responseBody.jsonObject).containsKeys("name")

            assertThat(responseBody.findFirstChildByPath("department")?.toStringLiteral()).isEqualTo("engineering")
            assertThat(responseBody.findFirstChildByPath("location")?.toStringLiteral()).isEqualTo("Mumbai")
        }
    }

    @Test
    fun `stub should honor schema key optionality in response returned using a partial example`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_and_optional_key_in_response_body.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("POST", "/person", body = parsedJSONObject("""{"name": "Stan", "department": "engineering"}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue

            assertThat(responseBody.jsonObject).containsKeys("id")

            assertThat(responseBody.findFirstChildByPath("location")?.toStringLiteral()).isEqualTo("Mumbai")
        }
    }

    @Test
    fun `stub should match partial query params`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_and_key_in_query.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", "/person", queryParametersMap = mapOf("category" to "technology"))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue

            assertThat(responseBody.jsonObject).containsKeys("id")

            assertThat(responseBody.findFirstChildByPath("location")?.toStringLiteral()).isEqualTo("Chennai")
        }
    }

    @Test
    fun `stub generate optional templated response header`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_of_optional_response_header.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", "/person", queryParametersMap = mapOf("category" to "technology"))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)

            assertThat(response.headers["X-Trace-ID"]).isEqualTo("abc123")
            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.jsonObject).containsKeys("id")
        }
    }

    @Test
    fun `stub should fail to match request missing mandatory query params`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_and_mandatory_key_in_query.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", "/person", queryParametersMap = mapOf("category" to "technology"))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(400)
            assertThat(response.body.toStringLiteral()).contains(">> REQUEST.QUERY-PARAMS.words")
        }
    }

    @Test
    fun `stub should match partial request headers`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_and_key_in_header.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", "/person", headers = mapOf("category" to "technology"))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue

            assertThat(responseBody.jsonObject).containsKeys("id")

            assertThat(responseBody.findFirstChildByPath("location")?.toStringLiteral()).isEqualTo("Chennai")
        }
    }

    @Test
    fun `stub should fail to match request missing mandatory request header`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_and_mandatory_key_in_header.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", "/person", headers = mapOf("category" to "technology"))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(400)
            assertThat(response.body.toStringLiteral()).contains(">> REQUEST.HEADERS.words")
        }
    }

    @Test
    fun `stub should match with request bodies with header in request and response templated`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_multiple_pieces_and_one_templated_item.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("POST", path = "/person", headers = mapOf("X-Trace-ID" to "abc123"), body = parsedJSONObject("""{"department": "marketing"}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)

            assertThat(response.headers["X-Trace-ID"]).isEqualTo("abc123")

            val responseBody = response.body as JSONObjectValue

            assertThat(responseBody.jsonObject).containsKeys("id")
            assertThat(responseBody.jsonObject).containsEntry("location", StringValue("Mumbai"))
        }
    }

    @Test
    fun `partial with invalid json value should get rejected` () {
        val (output, _) = captureStandardOutput {
            val stub = createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_invalid_partial_json_value.yaml")), timeoutMillis = 0)
            stub.close()
        }

        println(output)

        assertThat(output).contains(">> REQUEST.BODY.department")
        assertThat(output).contains(">> REQUEST.PATH.personId")
        assertThat(output).contains(">> REQUEST.HEADERS.id")
        assertThat(output).contains(">> REQUEST.QUERY-PARAMS.data")
        assertThat(output).contains(">> RESPONSE.HEADERS.data")
        assertThat(output).contains(">> RESPONSE.BODY.location")
    }

    @Test
    fun `ability to set dynamic expectations using partial examples`() {
        val feature = OpenApiSpecification.fromYAML("""
            openapi: 3.0.0
            info:
              title: Sample API
              version: 0.1.9
            paths:
              /person:
                post:
                  summary: Add person
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - name
                          properties:
                            name:
                              type: string
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema:
                            type: object
                            required:
                              - id
                              - name
                            properties:
                              id:
                                type: number
                              name:
                                type: string
        """.trimIndent(), "").toFeature()

        HttpStub(feature).use { stub ->
            val partialExample = """
                {
                    "partial": {
                        "http-request": {
                            "method": "POST",
                            "path": "/person",
                            "body": {
                                "name": "(NAME:string)"
                            }
                        },
                        "http-response": {
                            "status": 200,
                            "body": {
                                "name": "$(NAME)"
                            }
                        }
                    }
                }
            """.trimIndent()

            val expectationRequest = HttpRequest("POST", path = "/_specmatic/expectations", body = parsedJSONObject(partialExample))
            stub.client.execute(expectationRequest).also { response ->
                assertThat(response.status).isEqualTo(200)
            }

            val response = stub.client.execute(
                HttpRequest(
                    "POST",
                    path = "/person",
                    body = parsedJSONObject("""{"name": "Juste"}""")
                )
            )

            assertThat(response.status).isEqualTo(200)
            val jsonResponseBody = response.body as JSONObjectValue
            assertThat(jsonResponseBody.jsonObject).containsKey("id")
            assertThat(jsonResponseBody.jsonObject).containsEntry("name", StringValue("Juste"))
        }
    }

    @Test
    fun `partial match of AnyPattern in response`() {
        createStubFromContracts(listOf("src/test/resources/openapi/substitutions/spec_with_oneOf_in_response.yaml"), timeoutMillis = 0).use { stub ->
            stub.client.execute(HttpRequest("POST", "/person", body = parsedJSONObject("""{"name": "Jane"}"""))).also { response ->
                assertThat(response.status).isEqualTo(200)

                val jsonResponseBody = response.body as JSONObjectValue
                assertThat(jsonResponseBody.findFirstChildByPath("customername")?.toStringLiteral()).isEqualTo("Jane")
            }

            stub.client.execute(HttpRequest("POST", "/person", body = parsedJSONObject("""{"name": "Joan"}"""))).also { response ->
                assertThat(response.status).isEqualTo(200)

                val jsonResponseBody = response.body as JSONObjectValue
                assertThat(jsonResponseBody.findFirstChildByPath("employeename")?.toStringLiteral()).isEqualTo("Joan")
            }
        }
    }
}