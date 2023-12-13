package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GenerativeTests {
    @Test
    fun `generative tests for enums when an example is provided`() {
        val feature = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: Create person record
                  requestBody:
                    content:
                      application/json:
                        examples:
                          CREATE_PERSON:
                            value:
                              status: "active"
                        schema:
                          required:
                          - status
                          properties:
                            status:
                              type: string
                              enum:
                                - active
                                - inactive
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: "string"
                          examples:
                            CREATE_PERSON:
                              value:
                                "Person record created"
            """.trimIndent(), "").toFeature()

        val statusesSeen = mutableSetOf<String>()

        try {
            feature.copy(generativeTestingEnabled = true).executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val body = request.body as JSONObjectValue
                    println(body.toStringLiteral())

                    when (body.jsonObject["status"]!!) {
                        is StringValue -> statusesSeen.add(body.jsonObject["status"]!!.toStringLiteral())
                        else -> statusesSeen.add(body.jsonObject["status"]!!.displayableType())
                    }

                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })
        } finally {
            System.clearProperty(Flags.onlyPositive)
        }

        assertThat(statusesSeen).isEqualTo(setOf("active", "inactive", "null", "boolean", "number"))
    }

    @Test
    fun `generative tests for an optional key given a payload example without the optional key`() {
        val feature = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: Create person record
                  requestBody:
                    content:
                      application/json:
                        examples:
                          CREATE_PERSON:
                            value:
                              name: "John Doe"
                        schema:
                          required:
                          - name
                          properties:
                            name:
                              type: string
                            description:
                              type: string
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: "string"
                          examples:
                            CREATE_PERSON:
                              value:
                                "Person record created"
            """.trimIndent(), "").toFeature()

        val fromExample = 1
        val positiveGenerated = 1
        val negativeGenerativeAll = 3 + 3
        val negativeGenerativeNothing = 3


        try {
            val results = runGenerativeTests(feature)

            val expectedCountOfTests = fromExample + positiveGenerated + negativeGenerativeAll + negativeGenerativeNothing

            assertThat(results.results).hasSize(expectedCountOfTests)
        } catch(e: ContractException) {
            println(e.report())

            throw e
        }
    }

    @Test
    fun `generative tests for headers`() {
        val feature = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: Create person record
                  parameters:
                    - name: X-Request-Id
                      in: header
                      required: true
                      schema:
                        type: integer
                      examples:
                        CREATE_PERSON:
                          value: 987
                  requestBody:
                    content:
                      application/json:
                        examples:
                          CREATE_PERSON:
                            value:
                              name: "John Doe"
                        schema:
                          required:
                          - name
                          properties:
                            name:
                              type: string
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: object
                            required:
                              - id
                            properties:
                              id:
                                type: integer
                          examples:
                            CREATE_PERSON:
                              value:
                                id: 123
            """.trimIndent(), "").toFeature()

        try {
            val results = runGenerativeTests(feature)
            assertThat(results.results).hasSize(6)
        } catch(e: ContractException) {
            println(e.report())
            throw e
        }
    }

    @Test
    fun `generative tests for headers that are optional`() {
        val feature = OpenApiSpecification.fromYAML("""
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: Create person record
                  parameters:
                    - name: X-Request-Id
                      in: header
                      schema:
                        type: integer
                      examples:
                        CREATE_PERSON:
                          value: 987
                  requestBody:
                    content:
                      application/json:
                        examples:
                          CREATE_PERSON:
                            value:
                              name: "John Doe"
                        schema:
                          required:
                          - name
                          properties:
                            name:
                              type: string
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: object
                            required:
                              - id
                            properties:
                              id:
                                type: integer
                          examples:
                            CREATE_PERSON:
                              value:
                                id: 123
            """.trimIndent(), "").toFeature()

        try {
            val results = runGenerativeTests(feature)
            assertThat(results.results).hasSize(7)
        } catch(e: ContractException) {
            println(e.report())
            throw e
        }
    }

    @Test
    fun temp() {
        val feature =
            OpenApiSpecification
                .fromFile("src/test/resources/openapi/helloWithOneQueryParam.yaml")
                .toFeature()
                .copy(generativeTestingEnabled = true)

        feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                println(request.queryParams)
                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })
    }

    private fun runGenerativeTests(feature: Feature, generative: Boolean = true, onlyPositive: Boolean = false): Results {
        try {
            if (onlyPositive) {
                System.setProperty(Flags.onlyPositive, "true")
            }

            return feature.copy(generativeTestingEnabled = generative).executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    println(request.toLogString())
                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })
        } finally {
            System.clearProperty(Flags.onlyPositive)
        }
    }

}