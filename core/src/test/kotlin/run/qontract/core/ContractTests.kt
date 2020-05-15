package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.checkerframework.common.value.qual.StringVal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import run.qontract.core.Contract.Companion.fromGherkin
import run.qontract.core.pattern.NumberTypePattern
import run.qontract.core.value.*
import run.qontract.test.TestExecutor
import kotlin.test.*

class ContractTests {
    @Test
    @Throws(Throwable::class)
    fun shouldBeAbleToGetFakeFromContract() {
        val contract = fromGherkin(contractGherkin, 1, 0)
        contract.startFake(8080).close()
    }

    @Test
    @Throws(Throwable::class)
    fun shouldBeAbleToRunTestFromContract() {
        val contract = fromGherkin(contractGherkin, 1, 0)
        contract.startFake(8080).use { fake -> contract.test(fake.endPoint) }
    }

    @Test
    @Throws(Throwable::class)
    fun shouldBeAbleToTestFakeObject() {
        val contract = fromGherkin(contractGherkin, 1, 0)
        contract.startFake(8080).use { fake -> contract.test(fake) }
    }

    @Test
    @Throws(Throwable::class)
    fun shouldBeAbleToTestFakeObjectWithPath() {
        val contract = fromGherkin(pathParameterContractGherkin, 1, 0)
        contract.startFake(8080).use { fake -> contract.test(fake) }
    }

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

        val contract = ContractBehaviour(gherkin)
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

        val contract = ContractBehaviour(gherkin)
        val flags = mutableMapOf<String, Int>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestBody = request.body
                if (requestBody is JSONObjectValue) {
                    flags["optional"] = requestBody.jsonObject.getOrDefault("optional", 0).toString().toInt()
                }

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertEquals(10, flags["optional"])
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

        val contract = ContractBehaviour(gherkin)
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

        val contract = ContractBehaviour(gherkin)
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

        val contract = ContractBehaviour(gherkin)
        val flags = mutableMapOf<String, Boolean>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertTrue(NumberTypePattern.parse(request.formFields.getValue("number"), Resolver()) is NumberValue)
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

        val contract = ContractBehaviour(gherkin)
        val flags = mutableMapOf<String, Boolean>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertTrue(NumberTypePattern.parse(request.formFields.getValue("number"), Resolver()) is NumberValue)
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
        var pathParameterContractGherkin = """
                Feature: Contract for the balance service
                
                Scenario Outline: Should be able to get the balance for an individual
                  Given fact userid
                  When GET /balance/(userid:number)
                  Then status 200
                  And response-header Content-Length (number)
                  And response-body {call-mins-left: "(number)", sms-messages-left: "(number)"}

                Examples:
                | userid |
                | 12345  |
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

        val contract = ContractBehaviour(gherkin)
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

        val contract = ContractBehaviour(gherkin)
        val flags = mutableSetOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val body = request.body

                when (body) {
                    is NumberValue -> "json"
                    is NullValue -> "null"
                    else -> fail("Expected only json or null, got ${body?.javaClass}")
                }.let { flags.add(it) }

                return HttpResponse(200, "100")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertEquals(mutableSetOf("null", "json"), flags)
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

        val contract = ContractBehaviour(gherkin)

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

        val contract = ContractBehaviour(gherkin)
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

        val contract = ContractBehaviour(gherkin)
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

        val contract = ContractBehaviour(gherkin)
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

        val contract = ContractBehaviour(gherkin)
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

        val contract = ContractBehaviour(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                when {
                    "type" in request.queryParams -> flags.add("with")
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

        val contract = ContractBehaviour(gherkin)
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

        val contract = ContractBehaviour(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags.add("executed")

                val part = request.multiPartFormData.single() as MultiPartContentValue
                assertThat(part.name).isEqualTo("number")
                assertDoesNotThrow { part.content.toStringValue().toInt() }

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

        val contract = ContractBehaviour(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags.add("executed")

                val part = request.multiPartFormData.single() as MultiPartFileValue
                assertThat(part.name).isEqualTo("number")
                assertThat(part.filename).isEqualTo("@number.txt")
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
    fun `should generate a test with a multipart file part with no content ypte` () {
        val gherkin = """
Feature: Dumb API

Scenario: api call
When POST /number
And request-part number @number.txt
Then status 200
""".trim()

        val contract = ContractBehaviour(gherkin)
        val flags = mutableListOf<String>()

        val results = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags.add("executed")

                val part = request.multiPartFormData.single() as MultiPartFileValue
                assertThat(part.name).isEqualTo("number")
                assertThat(part.filename).isEqualTo("@number.txt")

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
And request-body (number from number)
Then status 200

Examples:
| number |
| 10     |
""".trim()

        val contract = ContractBehaviour(gherkin)
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
}

fun flagsContain(haystack: List<String>, needles: List<String>) {
    println("Haystack: $haystack")
    println("Needles: $needles")
    assertNull(needles.find { it !in haystack })
}
