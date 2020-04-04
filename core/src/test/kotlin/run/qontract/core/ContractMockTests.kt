package run.qontract.core

import run.qontract.core.pattern.NumberTypePattern
import run.qontract.core.pattern.NumericStringPattern
import run.qontract.core.pattern.StringPattern
import run.qontract.core.pattern.asValue
import run.qontract.core.utilities.parseXML
import run.qontract.mock.ContractMock
import run.qontract.mock.MockScenario
import run.qontract.mock.NoMatchingScenario
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.w3c.dom.Node
import run.qontract.core.value.NullValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.True
import run.qontract.core.value.Value
import java.net.URI
import java.util.*
import kotlin.collections.HashMap

class ContractMockTests {
    private val contractGherkin = """
Feature: Contract for the balance service

Scenario: JSON API to get list of locations
  Given pattern Location {"id": "(number)", "name": "(string)"}
  When GET /locations
  Then status 200
  And response-body {"locations": ["(Location...)"]}
  And response-header Content-Type application/json

Scenario: JSON API to get list of special locations
  Given pattern Location {"id": "(number)", "name": "(string)"}
  And pattern Locations {"locations": ["(Location...)"]}
  When GET /special_locations
  Then status 200
  And response-header Content-Type application/json
  And response-body (Locations)

Scenario: JSON API to create list of locations
  Given pattern Location {"id": "(number)", "name": "(string)"}
  And pattern Locations {"locations": ["(Location...)"]}
  When POST /locations
  And request-body (Locations)
  Then status 200
  And response-header Content-Type application/json

Scenario: JSON API to get account details with fact check
  Given fact userid
  When GET /account_json?userid=(number)
  Then status 200
  And response-body {"name": "(string)"}
  And response-header Content-Type application/json"""

    private val queryParamJsonContract = """
            Feature: Contract for the balance service
              Scenario: JSON API to get the balance for an individual
                When GET /balance_json?userid=(number)
                Then status 200
                And response-body {"call-mins-left": "(number)", "sms-messages-left": "(number)"}
                And response-header Content-Type application/json
    """

    private val pathParametersJsonContract = """
            Feature: Contract for the balance service
              Scenario: JSON API to get the balance for an individual
                When GET /balance_json/(userid:number)
                Then status 200
                And response-body {"call-mins-left": "(number)", "sms-messages-left": "(number)"}
                And response-header Content-Type application/json
            """

    private val pathParametersXmlContract = """
            Feature: Contract for the balance service
              Scenario: XML API to get the balance for an individual 
                When GET /balance_xml/(userid:number) 
                Then status 200 
                And response-body <balance><calls_left>100</calls_left><sms_messages_left>(number)</sms_messages_left></balance>
                And response-header Content-Type application/xml
            """

    private val queryParameterXmlContract = """
            Feature: Contract for the balance service
              Scenario: XML API to get the balance for an individual
                When GET /balance_xml?userid=(number)
                Then status 200
                And response-body <balance><calls_left>100</calls_left><sms_messages_left>(number)</sms_messages_left></balance>
                And response-header Content-Type application/xml
            """

    @TestFactory
    fun `mock should validate expectations and serve json`() = listOf(
            queryParamJsonContract to HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10"),
            pathParametersJsonContract to HttpRequest().updateMethod("GET").updatePath("/balance_json/10")
    ).map { (contractGherkinString, httpRequest) ->
        DynamicTest.dynamicTest("when url is ${httpRequest.getURL("")}") {
            val httpResponse = HttpResponse.jsonResponse("{call-mins-left: 100, sms-messages-left: 200}")
            val response = validateAndRespond(contractGherkinString, httpRequest, httpResponse, HashMap())
            val jsonResponse = JSONObject(Objects.requireNonNull(response.body))
            Assertions.assertEquals(200, response.statusCodeValue)
            Assertions.assertEquals(100, jsonResponse["call-mins-left"])
            Assertions.assertEquals(200, jsonResponse["sms-messages-left"])
        }
    }

    @TestFactory
    fun `mock should validate expectations and serve xml`() = listOf(
            queryParameterXmlContract to HttpRequest().updateMethod("GET").updatePath("/balance_xml").updateQueryParam("userid", "10"),
            pathParametersXmlContract to HttpRequest().updateMethod("GET").updatePath("/balance_xml/10")
    ).map { (contractGherkinString, httpRequest) ->
        DynamicTest.dynamicTest("when url is ${httpRequest.getURL("")}") {
            val expectedResponse = HttpResponse.xmlResponse("<balance><calls_left>100</calls_left><sms_messages_left>200</sms_messages_left></balance>")
            val response = validateAndRespond(contractGherkinString, httpRequest, expectedResponse, HashMap())
            val xmlResponse = parseXML(response.body)
            val root: Node = xmlResponse.documentElement
            Assertions.assertEquals("balance", root.nodeName)
            Assertions.assertEquals("calls_left", root.firstChild.nodeName)
            Assertions.assertEquals("100", root.firstChild.firstChild.nodeValue)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should fail to match invalid body`() {
        ContractMock.fromGherkin(queryParamJsonContract).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10")
            val expectedResponse = HttpResponse.jsonResponse("{call-mins-left: 100, smses-left: 200}")
            Assertions.assertThrows(NoMatchingScenario::class.java) { mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, HashMap())) }
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should validate expectations and serve generated json`() {
        val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.jsonResponse("{call-mins-left: \"(number)\", sms-messages-left: 200}")
        val response = validateAndRespond(queryParamJsonContract, expectedRequest, expectedResponse, HashMap())
        val jsonResponse = JSONObject(Objects.requireNonNull(response.body))
        Assertions.assertEquals(200, response.statusCodeValue)
        Assertions.assertTrue(NumberTypePattern().matches(asValue(jsonResponse["call-mins-left"]), Resolver()) is Result.Success)
        Assertions.assertEquals(200, jsonResponse["sms-messages-left"])
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should validate and serve response headers`() {
        val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.jsonResponse("{call-mins-left: \"(number)\", sms-messages-left: 200}").also {
            it.headers["token"] = "test"
        }
        val response = validateAndRespond("""
                Feature: Contract for the balance service
                  Scenario: JSON API to get the balance for an individual
                    When GET /balance_json?userid=(number)
                    Then status 200
                    And response-body {"call-mins-left": "(number)", "sms-messages-left": "(number)"}
                    And response-header token test
                    And response-header Content-Type application/json
        """, expectedRequest, expectedResponse, HashMap())
        val jsonResponse = JSONObject(Objects.requireNonNull(response.body))
        assertThat(response.statusCodeValue).isEqualTo(200)
        assertThat(NumberTypePattern().matches(asValue(jsonResponse["call-mins-left"]), Resolver()) is Result.Success).isTrue()
        assertThat(jsonResponse["sms-messages-left"]).isEqualTo(200)
        assertThat(response.headers["token"]).contains("test")
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should validate expectations and serve generated xml`() {
        val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/balance_xml").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.xmlResponse("<balance><calls_left>100</calls_left><sms_messages_left>(number)</sms_messages_left></balance>")
        val response = validateAndRespond(queryParameterXmlContract, expectedRequest, expectedResponse, HashMap())
        val xmlResponse = parseXML(response.body)
        val root: Node = xmlResponse.documentElement
        Assertions.assertEquals("balance", root.nodeName)
        Assertions.assertEquals("sms_messages_left", root.lastChild.nodeName)
        Assertions.assertTrue(NumericStringPattern().matches(asValue(root.firstChild.lastChild.nodeValue), Resolver()) is Result.Success)
    }

    //TODO: Why does this pass after mismatching userid in serverstate and queryParam?
    @Test
    @Throws(Throwable::class)
    fun `contract should mock server state`() {
        val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/account_json").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.jsonResponse("{\"name\": \"John Doe\"}")
        val serverState: HashMap<String, Value> = hashMapOf("userid" to NumberValue(10))
        val response = validateAndRespond(contractGherkin, expectedRequest, expectedResponse, serverState)
        val jsonResponse = JSONObject(Objects.requireNonNull(response.body))
        Assertions.assertEquals(200, response.statusCodeValue)
        Assertions.assertTrue(StringPattern().matches(asValue(jsonResponse["name"]), Resolver()) is Result.Success)
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock multi valued arrays in request body`() {
        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/locations")
            val responseBody = "{\"locations\": [{\"id\": 123, \"name\": \"Mumbai\"}, {\"id\": 123, \"name\": \"Mumbai\"}]}"
            val expectedResponse = HttpResponse.jsonResponse(responseBody)
            val emptyServerState = emptyMap<String, Value>().toMutableMap()
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, emptyServerState))
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock multi valued arrays using pattern in request body`() {
        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/special_locations")
            val responseBody = "{\"locations\": [{\"id\": 123, \"name\": \"Mumbai\"}, {\"id\": 123, \"name\": \"Mumbai\"}]}"
            val expectedResponse = HttpResponse.jsonResponse(responseBody)
            val emptyServerState = emptyMap<String, Value>().toMutableMap()
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, emptyServerState))
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock multi valued arrays using pattern in response body`() {
        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val requestBody = "{\"locations\": [{\"id\": 123, \"name\": \"Mumbai\"}, {\"id\": 123, \"name\": \"Mumbai\"}]}"
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/locations").updateBody(requestBody)
            val expectedResponse = HttpResponse.EMPTY_200.also {
                it.headers["Content-Type"] = "application/json"
            }
            val emptyServerState = HashMap<String, Value>()
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, emptyServerState))
        }
    }

    @Test
    fun `contract mock should function without needing complex fixture setup`() {
        val contractGherkin = """Feature: Contract for /locations API
  Scenario Outline: 
    * fixture city_list {"cities": [{"city": "Mumbai"}, {"city": "Bangalore"}]}
    * pattern City {"city": "(string)"}
    * pattern Cities {"cities": ["(City...)"]}
    Given fact cities_exist 
    When GET /locations
    Then status 200
    And response-body (Cities)
    And response-header Content-Type text/plain
    
  Examples:
  | cities_exist | 
  | city_list | 
    """
        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/locations")
            val expectedResponse = HttpResponse(200, "{\"cities\":[{\"city\": \"Mumbai\"}, {\"city\": \"Bangalore\"}] }")
            val serverState: HashMap<String, Value> = hashMapOf("cities_exist" to True)
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, serverState))
        }
    }

    @Throws(Throwable::class)
    private fun validateAndRespond(contractGherkinString: String, httpRequest: HttpRequest, httpResponse: HttpResponse, serverState: java.util.HashMap<String, Value>): ResponseEntity<String> {
        ContractMock.fromGherkin(contractGherkinString).use { mock ->
            mock.start()
            mock.createMockScenario(MockScenario(httpRequest, httpResponse, serverState))
            val restTemplate = RestTemplate()
            return restTemplate.exchange(URI.create(httpRequest.getURL("http://localhost:8080")), HttpMethod.GET, null, String::class.java)
        }
    }

    @Test
    fun `contract mock should be able to match integer in request and response bodies`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline:
    When POST /number
    And request-body (number)
    Then status 200
    And response-body (number)
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/number").updateBody("10")
            val expectedResponse = HttpResponse(200, "10")
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, HashMap()))
        }
    }

    @Test
    fun `should be able to mock out a number in the request for AnyPattern with number or null`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline:
    When POST /number
    And request-body (number?)
    Then status 200
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/number").updateBody(NumberValue(10))
            val expectedResponse = HttpResponse(200)
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, HashMap()))
        }
    }

    @Test
    fun `should be able to mock out a null in the request for AnyPattern with number or null`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline:
    When POST /number
    And request-body (number?)
    Then status 200
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/number").updateBody(NullValue)
            val expectedResponse = HttpResponse(200)
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, HashMap()))
        }
    }

    @Test
    fun `should be able to mock out a number in the response for AnyPattern with number or null`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline:
    When GET /number
    Then status 200
    And response-body (number)
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/number")
            val expectedResponse = HttpResponse(200, "10")
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, HashMap()))
        }
    }

    @Test
    fun `should be able to mock out an empty response for AnyPattern with number or null`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline:
    When GET /number
    Then status 200
    And response-body (number?)
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/number")
            val expectedResponse = HttpResponse(200, "")
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse, HashMap()))
        }
    }
}
