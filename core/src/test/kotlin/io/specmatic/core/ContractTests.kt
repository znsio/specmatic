package io.specmatic.core

import io.specmatic.conversions.OpenApiSpecification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.*
import io.specmatic.osAgnosticPath
import io.specmatic.test.HttpClient
import io.specmatic.test.TestExecutor
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.specmatic.stub.createStubFromContracts
import io.specmatic.stub.httpRequestLog
import io.specmatic.stub.httpResponseLog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.fail

class ContractTests {
    @Test
    fun `contract with one optional key and no examples should generate two tests` () {
        val gherkin = """
Feature: Older contract API

Scenario: api call
Given json Value
| value     | (number) |
| optional? | (number) |
When POST /value
And request-body (Value)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableMapOf<String, Int>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestBody = request.body
                if (requestBody is JSONObjectValue) {
                    when ("optional") {
                        in requestBody.jsonObject.keys -> "with"
                        else -> "without"
                    }.let { flags[it] = flags.getOrDefault(it, 0) + 1 }
                }

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertEquals(1, flags["with"])
        assertEquals(1, flags["without"])
        assertTrue(results.success(), results.report())
    }

    @Test
    fun `contract with one optional key and one example should generate one test` () {
        val gherkin = """
Feature: Older contract API

Scenario: api call
Given json Value
| value     | (number) |
| optional? | (number) |
When POST /value
And request-body (Value)
Then status 200

Examples:
| optional |
| 10       |
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val optionals = mutableListOf<Int>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestBody = request.body
                if (requestBody is JSONObjectValue) {
                    optionals.add(requestBody.jsonObject.getOrDefault("optional", 0).toString().toInt())
                }

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(optionals).contains(10)
        assertThat(optionals).hasSize(1)
        assertTrue(results.success(), results.report())
    }

    @Test
    fun `contract with one optional value should generate two tests` () {
        val gherkin = """
Feature: Contract API

Scenario: api call
Given json Value
| value     | (number?) |
When POST /value
And request-body (Value)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableSetOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestBody = request.body
                if (requestBody is JSONObjectValue) {
                    flags.add(when(requestBody.jsonObject.getOrDefault("value", null)) {
                        is NumberValue -> "number"
                        is NullValue -> "null"
                        else -> fail("Expected number or null")
                    })
                } else fail("Expected JSON object")

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertFalse(results.hasFailures(), results.report())
        assertEquals(mutableSetOf("null", "number"), flags)
    }

    @Test
    fun `when form fields exist in request, the corresponding content-type should exist in the headers` () {
        val gherkin = """
Feature: Math API

Scenario: api call
When POST /square
    And form-field number (number)
Then status 200
    And response-body (number)
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableMapOf<String, Boolean>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("application/x-www-form-urlencoded", request.headers.getOrDefault("Content-Type", ""))
                flags["parsed number"] = true
                return HttpResponse(200, "100")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertFalse(results.hasFailures(), results.report())
        assertTrue(flags.getValue("parsed number"))
    }

    @Test
    fun `form fields should be generated in a test` () {
        val gherkin = """
Feature: Math API

Scenario: api call
When POST /square
    And form-field number (number)
Then status 200
    And response-body (number)
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableMapOf<String, Boolean>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertTrue( NumberPattern().parse(request.formFields.getValue("number"), Resolver()) is NumberValue)
                flags["parsed number"] = true
                return HttpResponse(200, "100")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertFalse(results.hasFailures(), results.report())
        assertTrue(flags.getValue("parsed number"))
    }

    @Test
    fun `form fields specified as a table should be generated in a test` () {
        val gherkin = """
Feature: Math API

Scenario: api call
When POST /square
    And form-field
    | number | (number) |
Then status 200
    And response-body (number)
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableMapOf<String, Boolean>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertTrue( NumberPattern().parse(request.formFields.getValue("number"), Resolver()) is NumberValue)
                flags["parsed number"] = true
                return HttpResponse(200, "100")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertFalse(results.hasFailures(), results.report())
        assertTrue(flags.getValue("parsed number"))
    }

    companion object {
        var contractGherkin = """
            Feature: Contract for the balance service
                
                Scenario: Should be able to get the balance for an individual
                  Given fact userid
                  When GET /balance?userid=(number)
                  Then status 200
                  And response-header Content-Length (number)
                  And response-body {call-mins-left: "(number)", sms-messages-left: "(number)"}
            
                Examples:
                | userid |
                | 12345 |
                  
                Scenario: Should be able to get the balance for an individual
                  Given fact no_user
                  When GET /balance?userid=(number)
                  Then status 404
                  And response-header Content-Length (number)
            
                Examples:
                | userid |
                | 12345 |
                """
    }

    @Test
    fun `AnyPattern should generate the right type in a json value in a test` () {
        val gherkin = """
Feature: Math API

Scenario: api call
Given json Input
| value | (number?) |
When POST /square
    And request-body (Input)
Then status 200
    And response-body (number)
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableSetOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val body = request.body
                if(body is JSONObjectValue) {
                    val value = body.jsonObject.getValue("value")

                    flags.add(when (value) {
                        is NumberValue -> "json"
                        is NullValue -> "null"
                        else -> fail("Expected only json or null, got ${value.javaClass}")
                    })
                } else fail("Expected JSON object")

                return HttpResponse(200, "100")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertEquals(mutableSetOf("null", "json"), flags)
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `AnyPattern should generate a null value for a null body` () {
        val gherkin = """
Feature: Math API

Scenario: api call
When POST /square
    And request-body (number?)
Then status 200
    And response-body (number)
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableSetOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val body = request.body

                when (body) {
                    is NumberValue -> "json"
                    EmptyString -> "empty"
                    else -> fail("Expected only json or null, got ${body.javaClass}")
                }.let { flags.add(it) }

                return HttpResponse(200, "100")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertEquals(mutableSetOf("empty", "json"), flags)
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `AnyPattern should match a null value in the response body` () {
        val gherkin = """
Feature: Math API

Scenario: api call
When POST /square
    And request-body (number)
Then status 200
    And response-body (number?)
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(200, "")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `AnyPattern should pick up examples` () {
        val gherkin = """
Feature: Math API

Scenario: api call
Given json Input
| number | (number) |
When POST /square
    And request-body (Input)
Then status 200
    And response-body (number?)
Examples:
| number |
| 10 |
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        var invocationCount = 0

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                invocationCount = invocationCount.inc()

                val body = request.body

                if(body is JSONObjectValue) {
                    assertEquals(NumberValue(10), body.jsonObject.getValue("number"))
                } else fail("Expected JSON object")

                return HttpResponse(200, "")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertEquals(1, invocationCount)
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `should be able to pass null in example to AnyPattern` () {
        val gherkin = """
Feature: Math API

Scenario: api call
Given json Input
| number | (number?) |
When POST /square
    And request-body (Input)
Then status 200
Examples:
| number |
| (null) |
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        var invocationCount = 0

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                invocationCount = invocationCount.inc()

                val body = request.body

                if(body is JSONObjectValue) {
                    assertEquals(NullValue, body.jsonObject.getValue("number"))
                } else fail("Expected JSON object")

                return HttpResponse(200, "")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertEquals(1, invocationCount)
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `should generate a list when list operator is used` () {
        val gherkin = """
Feature: Dumb API

Scenario: api call
When POST /acceptNumber
And request-body (number*)
Then status 200
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        var invocationCount = 0

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                invocationCount = invocationCount.inc()

                val body = request.body

                if(body !is JSONArrayValue) fail("Expected JSON array")

                for(value in body.list) {
                    assertTrue(value is NumberValue)
                }

                return HttpResponse(200, "")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertEquals(1, invocationCount)
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `optional header should result in 2 tests` () {
        val gherkin = """
Feature: Dumb API

Scenario: api call
When GET /acceptNumber
And request-header X-Optional? (string)
Then status 200
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                println(request.headers)
                when {
                    "X-Optional" in request.headers -> flags.add("with")
                    else -> flags.add("without")
                }

                return HttpResponse(200, "")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        flagsContain(flags, listOf("with", "without"))
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `a single query param should result in 2 tests` () {
        val gherkin = """
Feature: Dumb API

Scenario: api call
When GET /queryNumber?type=(string)
Then status 200
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                when {
                    request.queryParams.containsKey("type") -> flags.add("with")
                    else -> flags.add("without")
                }

                return HttpResponse(200, "")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        flagsContain(flags, listOf("with", "without"))
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `an optional header should pick up its value from row` () {
        val gherkin = """
Feature: Dumb API

Scenario: api call
When GET /queryNumber
And request-header type? (string)
Then status 200

Examples:
| type      |
| some kind |
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags.add(request.headers["type"] ?: "")
                return HttpResponse(200, "")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(flags).isEqualTo(mutableListOf("some kind"))
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `should generate a test with a multipart content part` () {
        val gherkin = """
Feature: Dumb API

Scenario: api call
When POST /number
And request-part number (number)
Then status 200
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags.add("executed")

                val part = request.multiPartFormData.single() as MultiPartContentValue
                assertThat(part.name).isEqualTo("number")
                assertDoesNotThrow { part.content.toStringLiteral().toInt() }

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(flags).isEqualTo(mutableListOf("executed"))
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `should generate a test with a multipart file part` () {
        val gherkin = """
            Feature: Dumb API
            
            Scenario: api call
            When POST /number
            And request-part number @number.txt text/plain
            Then status 200
        """.trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags.add("executed")

                val part = request.multiPartFormData.single() as MultiPartFileValue
                assertThat(part.name).isEqualTo("number")
                assertThat(part.filename).endsWith(osAgnosticPath("/number.txt"))
                assertThat(part.contentType).isEqualTo("text/plain")

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(flags).isEqualTo(mutableListOf("executed"))
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `should generate a test with a multipart file part with no content type` () {
        val gherkin = """
            Feature: Dumb API
            
            Scenario: api call
            When POST /number
            And request-part number @number.txt
            Then status 200
        """.trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags.add("executed")

                val part = request.multiPartFormData.single() as MultiPartFileValue
                assertThat(part.name).isEqualTo("number")
                assertThat(part.filename).endsWith(osAgnosticPath("/number.txt"))

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(flags).isEqualTo(mutableListOf("executed"))
        assertFalse(results.hasFailures(), results.report())
    }

    @Test
    fun `a row lookup pattern should pick up it's value from a row and execute 1 test` () {
        val gherkin = """
Feature: Dumb API

Scenario: api call
When POST /number
And request-body (number:number)
Then status 200

Examples:
| number |
| 10     |
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableSetOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags.add("executed")
                assertThat(request.bodyString).isEqualTo("10")

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(flags.toSet()).isEqualTo(setOf("executed"))
        assertTrue(results.success(), results.report())
    }

    @Test
    fun `a multipart file type should pick up it's value from a row and execute tests` () {
        val gherkin = """
Feature: Has multipart

Scenario: multipart
When POST /data
And request-part employees @(string)
Then status 200

Examples:
| employees_filename |
| employees.csv |
""".trim()

        val contract = parseGherkinStringToFeature(gherkin)
        val flags = mutableSetOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags.add("executed")
                val filePart = request.multiPartFormData.first() as MultiPartFileValue
                assertThat(filePart.name).isEqualTo("employees")
                assertThat(filePart.filename).isEqualTo("employees.csv")

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(flags.toSet()).isEqualTo(setOf("executed"))
        assertTrue(results.success(), results.report())
    }

    @Test
    fun ` body and content-type header should be sent when there is a request body in the spec`() {
        val spec = OpenApiSpecification.fromYAML("""
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /test:
                post:
                  requestBody:
                    content:
                      text/plain:
                        schema:
                          type: string
                    required: true
                  responses:
                    '200':
                      description: OK
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        val server = embeddedServer(Netty, port = 8080) {
            routing {
                route("/{...}") {
                    handle {
                        assertThat(call.request.headers["Content-Type"]).isEqualTo("text/plain")
                        call.respondText("Hello, Ktor!")
                    }
                }
            }
        }

        try {
            server.start(wait = false)

            val results = spec.executeTests(HttpClient("http://localhost:8080"))
            assertThat(results.success()).withFailMessage(results.report()).isTrue()
            assertThat(results.successCount).isPositive()
        } finally {
            server.stop()
        }
    }

    @Test
    fun ` body and content-type header should not be sent when there is no request body in the spec`() {
        val spec = OpenApiSpecification.fromYAML("""
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /test:
                post:
                  responses:
                    '200':
                      description: OK
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        val server = embeddedServer(Netty, port = 8080) {
            routing {
                route("/{...}") {
                    handle {
                        assertThat(call.request.headers["Content-Type"]).isNull()
                        call.respondText("Hello, Ktor!")
                    }
                }
            }
        }

        try {
            server.start(wait = false)

            val results = spec.executeTests(HttpClient("http://localhost:8080"))
            assertThat(results.success()).withFailMessage(results.report()).isTrue()
            assertThat(results.successCount).isPositive()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `contract tests should encode spaces in path segments before sending the request`() {
        val pathWithSpace = "/rand om"

        val specification = OpenApiSpecification.fromYAML("""
            openapi: 3.0.1
            info:
              title: Random
              version: "1"
            paths:
              $pathWithSpace:
                post:
                  summary: Random
                  requestBody:
                    content:
                      application/json:
                        schema:
                          required:
                          - id
                          properties:
                            id:
                              type: number
                  responses:
                    "200":
                      description: Random
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        val pathsSeen = mutableListOf<String>()

        val server = embeddedServer(Netty, port = 9001) {
            routing {
                route("/{...}") {
                    handle {
                        pathsSeen.add(call.request.path())
                        call.respondText("Hello, Ktor!")
                    }
                }
            }
        }

        val results = try {
            server.start(wait = false)
            specification.executeTests(HttpClient("http://localhost:9001"))
        } finally {
            server.stop()
        }

        assertThat(results.success()).isTrue()
        assertThat(pathsSeen.distinct().first()).isEqualTo(escapeSpaceInPath(pathWithSpace))
    }

    @Test
    fun `contract tests should encode spaces in path segments when query params are present before sending the request`() {
        val pathWithSpace = "/rand om"

        val specification = OpenApiSpecification.fromYAML("""
            openapi: 3.0.1
            info:
              title: Random
              version: "1"
            paths:
              $pathWithSpace:
                post:
                  summary: Random
                  parameters:
                    - name: name
                      in: query
                      schema:
                        type: string
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - id
                          properties:
                            id:
                              type: number
                  responses:
                    "200":
                      description: Random
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        val pathsSeen = mutableListOf<String>()

        val server = embeddedServer(Netty, port = 9001) {
            routing {
                route("/{...}") {
                    handle {
                        pathsSeen.add(call.request.path())
                        call.respondText("Hello, Ktor!")
                    }
                }
            }
        }

        val results = try {
            server.start(wait = false)
            specification.executeTests(HttpClient("http://localhost:9001"))
        } finally {
            server.stop()
        }

        assertThat(results.success()).isTrue()
        assertThat(pathsSeen.distinct().first()).isEqualTo(escapeSpaceInPath(pathWithSpace))
    }

    @Test
    fun `contract tests should encode spaces in query params before sending the request`() {
        val queryParamWithSpace = "id entifier"

        val specification = OpenApiSpecification.fromYAML("""
            openapi: 3.0.1
            info:
              title: Random
              version: "1"
            paths:
              /random:
                post:
                  summary: Random
                  parameters:
                    - name: $queryParamWithSpace
                      in: query
                      schema:
                        type: string
                      examples:
                        SUCCESS:
                          value: "123"
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - id
                          properties:
                            id:
                              type: number
                        examples:
                          SUCCESS:
                            value:
                              id: 10
                  responses:
                    "200":
                      description: Random
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            SUCCESS:
                              value: "123"
        """.trimIndent(), "").toFeature()

        val queryParamsSeen = mutableListOf<Map<String, List<String>>>()

        val server = embeddedServer(Netty, port = 9001) {
            routing {
                route("/{...}") {
                    handle {
                        queryParamsSeen.add(call.request.queryParameters.toMap())
                        call.respondText("Hello, Ktor!")
                    }
                }
            }
        }

        val results = try {
            server.start(wait = false)
            specification.executeTests(HttpClient("http://localhost:9001"))
        } finally {
            server.stop()
        }

        assertThat(results.success()).isTrue()
        assertThat(queryParamsSeen.distinct()).containsExactlyInAnyOrder(
            mapOf(queryParamWithSpace to listOf("123")))
    }

    @Test
    fun `contract tests should consider any 4xx as valid negative-test responses`() {
        val specification = OpenApiSpecification.fromYAML("""
            openapi: 3.0.1
            info:
              title: Random
              version: "1"
            paths:
              /random:
                post:
                  summary: Random
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - id
                          properties:
                            id:
                              type: number
                        examples:
                          SUCCESS:
                            value:
                              id: 10
                  responses:
                    "200":
                      description: Random
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            SUCCESS:
                              value: "123"
                    "400":
                      description: Random
                      content:
                        text/plain:
                          schema:
                            type: string
                    "422":
                      description: Random
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), "").toFeature()

        val server = embeddedServer(Netty, port = 9001) {
            routing {
                route("/{...}") {
                    handle {
                        val body = parsedJSONObject(call.receiveText())

                        when(body.jsonObject["id"]) {
                            is NumberValue -> call.respondText("Hello, Ktor!")
                            is NullValue -> call.respond(HttpStatusCode.BadRequest)
                            is BooleanValue -> call.respond(HttpStatusCode.UnprocessableEntity)
                            is StringValue -> call.respond(HttpStatusCode.UnprocessableEntity)
                        }
                    }
                }
            }
        }

        val results = try {
            server.start(wait = false)
            specification.enableGenerativeTesting().executeTests(HttpClient("http://localhost:9001"))
        } finally {
            server.stop()
        }

        assertThat(results.success()).isTrue()
        assertThat(results.failureCount).isZero()
    }

    @Test
    fun `test should load an example for a spec with pattern as a path param`() {
        val feature: Feature = OpenApiSpecification.fromFile("src/test/resources/openapi/spec_with_path_param.yaml").toFeature().loadExternalisedExamples()

        var itemSeen: String = ""

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                itemSeen = request.queryParams.asMap().getValue("item")
                return HttpResponse.ok(parsedJSONObject("""{"id": 10}""")).also {
                    println(request.toLogString())
                    println()
                    println(it.toLogString())
                }
            }
        })

        assertThat(itemSeen).withFailMessage(results.report()).isEqualTo("10")
    }
}

fun flagsContain(haystack: List<String>, needles: List<String>) {
    println("Haystack: $haystack")
    println("Needles: $needles")
    assertNull(needles.find { it !in haystack })
}
