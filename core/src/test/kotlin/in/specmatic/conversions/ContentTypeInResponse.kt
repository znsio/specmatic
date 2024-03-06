package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContentTypeInResponse {
    @Test
    fun temp() {
        val feature = OpenApiSpecification.fromYAML("""
            openapi: 3.0.0
            info:
              title: "Order Service"
              version: "1.0.0"
            paths:
              /order:
                post:
                  requestBody:
                    content:
                      text/plain:
                        schema:
                          type: string
                  responses:
                    '200':
                      description: "Order created successfully"
                      content:
                        application/json:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        HttpStub(feature).use { stub ->
            val expectedRequest = HttpRequest("POST", "/order", body = StringValue("data"))
            val expectedResponse = HttpResponse(200, body = StringValue("data"))

            val expectation = ScenarioStub(expectedRequest, expectedResponse)

            assertThat(expectation.toJSON().toString()).doesNotContain("application/json")

            val response = stub.client.execute(HttpRequest("POST", "_specmatic_/expectations", body = expectation.toJSON()))

            assertThat(response.status).isEqualTo(400)
        }
    }
}