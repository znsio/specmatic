package integration_tests

import `in`.specmatic.conversions.EnvironmentAndPropertiesConfiguration
import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.Flags
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.pattern.parsedJSONObject
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExtendableSchema {
    @Test
    fun `when extendable schema is enabled, a JSON request object with unexpected keys should be accepted when running tests`() {
        val feature =
            OpenApiSpecification.fromYAML(
                """
openapi: 3.0.0
info:
    title: Test
    version: 1.0.0
paths:
    /test:
        post:
            requestBody:
                content:
                    application/json:
                        schema:
                            type: object
                            required:
                                - name
                            properties:
                                name:
                                    type: string
                        examples:
                            SUCCESS:
                                value:
                                    name: John
                                    address: "Baker street"
            responses:
                '200':
                    description: OK
                    content:
                      text/plain:
                          schema:
                              type: string
                          examples:
                              SUCCESS:
                                  value: success
            """.trimIndent(),
                "",
                environmentAndPropertiesConfiguration = EnvironmentAndPropertiesConfiguration(mapOf(), mapOf(Flags.EXTENSIBLE_SCHEMA to "true"))
            ).toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                println(request.toLogString())
                assertThat((request.body as JSONObjectValue).jsonObject).containsKey("address")
                return HttpResponse.ok("success")
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }

    @Test
    fun `when extendable schema is enabled, a JSON response object with unexpected keys should be accepted when running tests`() {
        val feature =
            OpenApiSpecification.fromYAML(
                """
openapi: 3.0.0
info:
    title: Test
    version: 1.0.0
paths:
    /test:
        post:
            requestBody:
                content:
                    application/json:
                        schema:
                            type: object
                            required:
                                - name
                            properties:
                                name:
                                    type: string
                        examples:
                            SUCCESS:
                                value:
                                    name: John
                                    address: "Baker street"
            responses:
                '200':
                    description: OK
                    content:
                      application/json:
                          schema:
                              type: object
                              properties:
                                    name:
                                        type: string
                          examples:
                              SUCCESS:
                                  value:
                                      name: John
            """.trimIndent(),
                "",
                environmentAndPropertiesConfiguration = EnvironmentAndPropertiesConfiguration(mapOf(), mapOf(Flags.EXTENSIBLE_SCHEMA to "true"))
            ).toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                println(request.toLogString())
                assertThat((request.body as JSONObjectValue).jsonObject).containsKey("address")
                return HttpResponse.ok(parsedJSONObject("""{"name": "John", "address": "Baker street"}"""))
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isTrue()
    }
}