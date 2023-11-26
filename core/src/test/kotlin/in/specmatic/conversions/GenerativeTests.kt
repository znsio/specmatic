package `in`.specmatic.conversions

import `in`.specmatic.core.Flags
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.BooleanValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.TestExecutor
import io.mockk.InternalPlatformDsl.toStr
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test

class GenerativeTests {
    @Test
    fun `handle request payloads with the same key in different parts of the payload with different types`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.0
            info:
              title: Addresses
              version: 1.0.0
            paths:
              /addresses:
                post:
                  requestBody:
                    content:
                      application/json:
                        examples:
                          SUCCESS:
                            value:
                              person:
                                address:
                                  building: 1
                              company:
                                address:
                                  building: "Bldg no 1"
                        schema:
                          type: object
                          required:
                            - person
                            - company
                          properties:
                            person:
                              type: object
                              required:
                                - address
                              properties:
                                address:
                                  type: object
                                  required:
                                    - building
                                  properties:
                                    building:
                                      type: integer
                            company:
                              type: object
                              required:
                                - address
                              properties:
                                address:
                                  type: object
                                  required:
                                    - building
                                  properties:
                                    building:
                                      type: string
                  responses:
                    '200':
                      description: OK
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            SUCCESS:
                              value: OK
        """.trimIndent(), "").toFeature()

        val building = mutableListOf<String>()

        try {
            System.setProperty(Flags.onlyPositive, "true")
            val results = feature.copy(generativeTestingEnabled = true).executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val body = request.body as JSONObjectValue
                    building.add(body.findFirstChildByPath("person.address.building")!!.toStringLiteral())
                    building.add(body.findFirstChildByPath("company.address.building")!!.toStringLiteral())
                    return HttpResponse.OK("OK")
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })

            println(results.report())

            assertThat(results.failureCount).isEqualTo(0)
            assertThat(results.successCount).isGreaterThan(0)

            assertThat(building.toList().toSet()).isEqualTo(setOf("1", "Bldg no 1"))
        } catch(e: ContractException) {
            fail("Should not have got this error:\n${e.report()}")
        } finally {
            System.clearProperty(Flags.onlyPositive)
        }
    }

    @Test
    fun `handle request payloads with the same key in header and request payload with different types`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.0
            info:
              title: Result
              version: 1.0.0
            paths:
              /result:
                post:
                  parameters:
                    - name: status
                      in: header
                      schema:
                        type: boolean
                      examples:
                        SUCCESS:
                          value: true
                  requestBody:
                    content:
                      application/json:
                        examples:
                          SUCCESS:
                            value:
                              status: success
                        schema:
                          type: object
                          required:
                            - status
                          properties:
                            status:
                              type: string
                  responses:
                    '200':
                      description: OK
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            SUCCESS:
                              value: OK
        """.trimIndent(), "").toFeature()

        val building = mutableListOf<String>()

        try {
            System.setProperty(Flags.onlyPositive, "true")
            val results = feature.copy(generativeTestingEnabled = true).executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val body = request.body as JSONObjectValue
                    building.add(body.findFirstChildByPath("status")!!.toStringLiteral() + " in body")
                    building.add(request.headers.getValue("status") + " in headers")
                    return HttpResponse.OK("OK")
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })

            println(results.report())

            assertThat(results.failureCount).isEqualTo(0)
            assertThat(results.successCount).isGreaterThan(0)

            assertThat(building.toList().toSet()).isEqualTo(setOf("true in headers", "success in body"))
        } catch(e: ContractException) {
            fail("Should not have got this error:\n${e.report()}")
        } finally {
            System.clearProperty(Flags.onlyPositive)
        }
    }

    @Test
    fun `negative testing should directly use a given request payload`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.0
            info:
              title: Result
              version: 1.0.0
            paths:
              /result:
                post:
                  requestBody:
                    content:
                      application/json:
                        examples:
                          SUCCESS:
                            value:
                              status: true
                        schema:
                          type: object
                          required:
                            - status
                          properties:
                            status:
                              type: boolean
                  responses:
                    '200':
                      description: OK
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            SUCCESS:
                              value: OK
                    '400':
                      description: Bad Request
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        val buildingValuesSeen = mutableSetOf<String>()

        try {
            val results = feature.copy(generativeTestingEnabled = true).executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val body = request.body as JSONObjectValue
                    val status = body.findFirstChildByPath("status")!!

                    if(status is NullValue)
                        buildingValuesSeen.add("null")
                    else
                        buildingValuesSeen.add(status.toStringLiteral())

                    return if(status == BooleanValue(true))
                        HttpResponse.OK("OK")
                    else
                        HttpResponse(400, "Bad Request")
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })

            println(results.report())

            val expectedBuildingValues = setOf("null", "true")
            assertThat(buildingValuesSeen).isEqualTo(expectedBuildingValues)
        } catch(e: ContractException) {
            fail("Should not have got this error:\n${e.report()}")
        }
    }
}
