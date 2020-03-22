package run.qontract.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import run.qontract.core.Contract.Companion.forService
import run.qontract.core.Contract.Companion.fromGherkin
import run.qontract.core.utilities.brokerURL
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.NumberTypePattern
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.test.TestExecutor
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractTests {
    @Test
    @Throws(IOException::class)
    fun shouldBeAbleToGetContract() {
        val wireMockServer = WireMockServer(8089)
        wireMockServer.start()
        val majorVersion = 1
        val minorVersion = 0
        val mockResponseJSONObject: JSONObject = object : JSONObject() {
            init {
                put("majorVersion", majorVersion)
                put("minorVersion", minorVersion)
                put("spec", contractGherkin)
            }
        }
        val mapper = ObjectMapper()
        val jsonFactory = mapper.factory
        val jsonParser = jsonFactory.createParser(mockResponseJSONObject.toString())
        val mockResponse = mapper.readTree<JsonNode>(jsonParser)
        wireMockServer.stubFor(WireMock.get("/contracts?provider=balance&majorVersion=$majorVersion&minorVersion=$minorVersion").willReturn(WireMock.aResponse().withStatus(200).withJsonBody(mockResponse)))
        brokerURL = wireMockServer.baseUrl()
        val actual = forService("balance", majorVersion, minorVersion)
        val expected = fromGherkin(contractGherkin, majorVersion, minorVersion)
        Assertions.assertEquals(expected, actual)
        wireMockServer.stop()
    }

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

Scenario:
Given json Value
| value     | (number) |
| optional? | (number) |
When POST /value
And request-body (Value)
Then status 200
    """.trim()

        val contract = ContractBehaviour(gherkin)
        val flags = mutableMapOf<String, Int>()

        contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestBody = request.body
                if(requestBody is JSONObjectValue) {
                    when("optional") {
                        in requestBody.jsonObject.keys -> "with"
                        else -> "without"
                    }.let { flags[it] = flags.getOrDefault(it, 0) + 1 }
                }

                return HttpResponse(200)
            }

            override fun setServerState(serverState: Map<String, Any?>) {
            }
        })

        assertEquals(1, flags["with"])
        assertEquals(1, flags["without"])
    }

    @Test
    fun `contract with one optional key and one examples should generate one test` () {
        val gherkin = """
Feature: Older contract API

Scenario:
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

        contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestBody = request.body
                if(requestBody is JSONObjectValue) {
                    flags["optional"] = requestBody.jsonObject.getOrDefault("optional", 0).toString().toInt()
                }

                return HttpResponse(200)
            }

            override fun setServerState(serverState: Map<String, Any?>) {
            }
        })

        assertEquals(10, flags["optional"])
    }

    @Test
    fun `when form fields exist in request, the corresponding content-type should exist in the headers` () {
        val gherkin = """
Feature: Math API

Scenario:
When POST /square
    And form-field number (number)
Then status 200
    And response-body (number)
""".trim()

        val contract = ContractBehaviour(gherkin)
        val flags = mutableMapOf<String, Boolean>()

        val executionInfo = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("application/x-www-form-urlencoded", request.headers.getOrDefault("Content-Type", ""))
                flags["parsed number"] = true
                return HttpResponse(200, "100")
            }

            override fun setServerState(serverState: Map<String, Any?>) {
            }
        })

        if(executionInfo.hasErrors)
            executionInfo.print()

        assertFalse(executionInfo.hasErrors)
        assertTrue(flags.getValue("parsed number"))
    }

    @Test
    fun `form fields should be generated in a test` () {
        val gherkin = """
Feature: Math API

Scenario:
When POST /square
    And form-field number (number)
Then status 200
    And response-body (number)
""".trim()

        val contract = ContractBehaviour(gherkin)
        val flags = mutableMapOf<String, Boolean>()

        val executionInfo = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertTrue(NumberTypePattern().parse(request.formFields.getValue("number"), Resolver()) is NumberValue)
                flags["parsed number"] = true
                return HttpResponse(200, "100")
            }

            override fun setServerState(serverState: Map<String, Any?>) {
            }
        })

        if(executionInfo.hasErrors)
            executionInfo.print()

        assertFalse(executionInfo.hasErrors)
        assertTrue(flags.getValue("parsed number"))
    }

    @Test
    fun `form fields specified as a table should be generated in a test` () {
        val gherkin = """
Feature: Math API

Scenario:
When POST /square
    And form-field
    | number | (number) |
Then status 200
    And response-body (number)
""".trim()

        val contract = ContractBehaviour(gherkin)
        val flags = mutableMapOf<String, Boolean>()

        val executionInfo = contract.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertTrue(NumberTypePattern().parse(request.formFields.getValue("number"), Resolver()) is NumberValue)
                flags["parsed number"] = true
                return HttpResponse(200, "100")
            }

            override fun setServerState(serverState: Map<String, Any?>) {
            }
        })

        if(executionInfo.hasErrors)
            executionInfo.print()

        assertFalse(executionInfo.hasErrors)
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
}
