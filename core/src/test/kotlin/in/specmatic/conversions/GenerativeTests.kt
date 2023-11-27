package `in`.specmatic.conversions

import `in`.specmatic.core.Flags
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedJSONObject
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

        val statusValuesSeen = mutableSetOf<String>()

        try {
            System.setProperty(Flags.onlyPositive, "true")
            val results = feature.copy(generativeTestingEnabled = true).executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val body = request.body as JSONObjectValue
                    statusValuesSeen.add(body.findFirstChildByPath("status")!!.toStringLiteral() + " in body")
                    statusValuesSeen.add(request.headers.getValue("status") + " in headers")
                    return HttpResponse.OK("OK")
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })

            println(results.report())

            assertThat(results.failureCount).isEqualTo(0)
            assertThat(results.successCount).isGreaterThan(0)

            val expectedStatusValues = setOf("true in headers", "success in body")
            assertThat(statusValuesSeen).isEqualTo(expectedStatusValues)
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

        val buildingValuesSeen = mutableSetOf<String>()

        try {
            val results = feature.copy(generativeTestingEnabled = true).executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val body = request.body as JSONObjectValue
                    val personBuilding = body.findFirstChildByPath("person.address.building")!!

                    if(personBuilding is NullValue)
                        buildingValuesSeen.add("null")
                    else
                        buildingValuesSeen.add(personBuilding.toStringLiteral())

                    val companyBuilding = body.findFirstChildByPath("company.address.building")!!

                    if(companyBuilding is NullValue)
                        buildingValuesSeen.add("null")
                    else
                        buildingValuesSeen.add(companyBuilding.toStringLiteral())

                    return HttpResponse.OK("OK")
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })

            println(results.report())

            assertThat("1").isIn(buildingValuesSeen)
            assertThat("null").isIn(buildingValuesSeen)
            assertThat("Bldg no 1").isIn(buildingValuesSeen)
            assertThat(buildingValuesSeen).containsAnyOf("true", "false")
            assertThat(buildingValuesSeen.size).isEqualTo(6)
        } catch(e: ContractException) {
            fail("Should not have got this error:\n${e.report()}")
        }
    }


    @Test
    fun `generative positive-only tests with REQUEST-BODY example`() {
        val specification = OpenApiSpecification.fromYAML("""
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Get person by id"
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        examples:
                          SUCCESS:
                            value:
                              address:
                                - street: "1"

                        schema:
                          required:
                          - "address"
                          properties:
                            address:
                              type: "array"
                              items:
                                ${'$'}ref: "#/components/schemas/Address"
                  responses:
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
                          examples:
                            SUCCESS:
                              value: success
            components:
              schemas:
                Address:
                  properties:
                    street:
                      type: "string"
        """, "").toFeature()

        val requestBodiesSeen = mutableListOf<Value>()

        val results = specification.copy(generativeTestingEnabled = true).executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                println(request.body)
                requestBodiesSeen.add(request.body)
                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })
        println(results.report())

        assertThat(requestBodiesSeen).hasSize(3)
    }
}
