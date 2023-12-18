package `in`.specmatic.conversions

import `in`.specmatic.DefaultStrategies
import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions
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
            feature.copy(generativeTestingEnabled = true, resolverStrategies = DefaultStrategies.copy(generation = GenerativeTestsEnabled())).executeTests(object : TestExecutor {
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
            Assertions.fail("Should not have got this error:\n${e.report()}")
        } finally {
            System.clearProperty(Flags.onlyPositive)
        }
    }

    @Test
    fun `generative tests should correctly generate values of the right type where the same key appears with different types in the request payload`() {
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
            Assertions.fail("Should not have got this error:\n${e.report()}")
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

    private fun runGenerativeTests(feature: Feature, generative: Boolean = true, onlyPositive: Boolean = false): Results {
        try {
            if (onlyPositive) {
                System.setProperty(Flags.onlyPositive, "true")
            }

            return feature.copy(generativeTestingEnabled = generative, resolverStrategies = DefaultStrategies.copy(generation = GenerativeTestsEnabled())).executeTests(object : TestExecutor {
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
