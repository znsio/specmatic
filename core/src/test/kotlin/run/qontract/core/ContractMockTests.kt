package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.*
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import org.springframework.web.client.postForEntity
import org.w3c.dom.Node
import run.qontract.core.pattern.NumericStringPattern
import run.qontract.core.pattern.parsedJSON
import run.qontract.core.utilities.parseXML
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NullValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.mock.ContractMock
import run.qontract.mock.MockScenario
import run.qontract.mock.NoMatchingScenario
import java.net.URI
import java.util.*

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
            val response = validateAndRespond(contractGherkinString, httpRequest, httpResponse)
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
            val response = validateAndRespond(contractGherkinString, httpRequest, expectedResponse)

            response.body?.let {
                val xmlResponse = parseXML(it)
                val root: Node = xmlResponse.documentElement
                Assertions.assertEquals("balance", root.nodeName)
                Assertions.assertEquals("calls_left", root.firstChild.nodeName)
                Assertions.assertEquals("100", root.firstChild.firstChild.nodeValue)
            } ?: fail("Expected a response body")
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should fail to match invalid body`() {
        ContractMock.fromGherkin(queryParamJsonContract).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10")
            val expectedResponse = HttpResponse.jsonResponse("{call-mins-left: 100, smses-left: 200}")
            Assertions.assertThrows(NoMatchingScenario::class.java) { mock.createMockScenario(MockScenario(expectedRequest, expectedResponse)) }
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should validate expectations and serve generated json`() {
        val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.jsonResponse("{call-mins-left: \"(number)\", sms-messages-left: 200}")
        val response = validateAndRespond(queryParamJsonContract, expectedRequest, expectedResponse)
        val jsonResponse = JSONObject(Objects.requireNonNull(response.body))
        Assertions.assertEquals(200, response.statusCodeValue)
        assertThat(jsonResponse.get("call-mins-left")).isInstanceOf(Number::class.java)
        Assertions.assertEquals(200, jsonResponse["sms-messages-left"])
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should validate and serve response headers`() {
        val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.jsonResponse("{call-mins-left: \"(number)\", sms-messages-left: 200}").let { it.copy(headers = it.headers.plus("token" to "test"))}
        val response = validateAndRespond("""
                Feature: Contract for the balance service
                  Scenario: JSON API to get the balance for an individual
                    When GET /balance_json?userid=(number)
                    Then status 200
                    And response-body {"call-mins-left": "(number)", "sms-messages-left": "(number)"}
                    And response-header token test
                    And response-header Content-Type application/json
        """, expectedRequest, expectedResponse)
        val jsonResponse = JSONObject(Objects.requireNonNull(response.body))
        assertThat(response.statusCodeValue).isEqualTo(200)
        assertThat(jsonResponse.get("call-mins-left")).isInstanceOf(Number::class.java)
        assertThat(jsonResponse["sms-messages-left"]).isEqualTo(200)
        assertThat(response.headers["token"]).contains("test")
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should validate expectations and serve generated xml`() {
        val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/balance_xml").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.xmlResponse("<balance><calls_left>100</calls_left><sms_messages_left>(number)</sms_messages_left></balance>")
        val response = validateAndRespond(queryParameterXmlContract, expectedRequest, expectedResponse)

        response.body?.let {
            val xmlResponse = parseXML(it)
            val root: Node = xmlResponse.documentElement
            Assertions.assertEquals("balance", root.nodeName)
            Assertions.assertEquals("sms_messages_left", root.lastChild.nodeName)
            Assertions.assertTrue(NumericStringPattern().matches(StringValue(root.firstChild.lastChild.nodeValue), Resolver()) is Result.Success)
        } ?: fail("Expected body in the response")
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock server state`() {
        val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/account_json").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.jsonResponse("{\"name\": \"John Doe\"}")
        val response = validateAndRespond(contractGherkin, expectedRequest, expectedResponse)
        val jsonResponse = JSONObject(Objects.requireNonNull(response.body))
        Assertions.assertEquals(200, response.statusCodeValue)
        assertThat(jsonResponse["name"]).isInstanceOf(String::class.java)
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock multi valued arrays in request body`() {
        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/locations")
            val responseBody = "{\"locations\": [{\"id\": 123, \"name\": \"Mumbai\"}, {\"id\": 123, \"name\": \"Mumbai\"}]}"
            val expectedResponse = HttpResponse.jsonResponse(responseBody)
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
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
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock multi valued arrays using pattern in response body`() {
        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val requestBody = "{\"locations\": [{\"id\": 123, \"name\": \"Mumbai\"}, {\"id\": 123, \"name\": \"Mumbai\"}]}"
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/locations").updateBody(requestBody)
            val expectedResponse = HttpResponse.EMPTY_200.let { it.copy(headers = it.headers.plus("Content-Type" to "application/json"))}
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `contract mock should function without needing complex fixture setup`() {
        val contractGherkin = """Feature: Contract for /locations API
  Scenario Outline: api call
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
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
        }
    }

    @Throws(Throwable::class)
    private fun validateAndRespond(contractGherkinString: String, httpRequest: HttpRequest, httpResponse: HttpResponse): ResponseEntity<String> {
        ContractMock.fromGherkin(contractGherkinString).use { mock ->
            mock.start()
            mock.createMockScenario(MockScenario(httpRequest, httpResponse))
            val restTemplate = RestTemplate()
            return restTemplate.exchange(URI.create(httpRequest.getURL("http://localhost:8080")), HttpMethod.GET, null, String::class.java)
        }
    }

    @Test
    fun `contract mock should be able to match integer in request and response bodies`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline: api call
    When POST /number
    And request-body (number)
    Then status 200
    And response-body (number)
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/number").updateBody("10")
            val expectedResponse = HttpResponse(200, "10")
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `should be able to mock out a number in the request for AnyPattern with number or null`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline: api call
    When POST /number
    And request-body (number?)
    Then status 200
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/number").updateBody(NumberValue(10))
            val expectedResponse = HttpResponse(200)
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `should be able to mock out a null in the request for AnyPattern with number or null`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline: api call
    When POST /number
    And request-body (number?)
    Then status 200
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/number").updateBody(NullValue)
            val expectedResponse = HttpResponse(200)
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `should be able to mock out a number in the response for AnyPattern with number or null`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline: api call
    When GET /number
    Then status 200
    And response-body (number)
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/number")
            val expectedResponse = HttpResponse(200, "10")
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `should be able to mock out an empty response for AnyPattern with number or null`() {
        val contractGherkin = """Feature: Contract for /number API
  Scenario Outline: api call
    When GET /number
    Then status 200
    And response-body (number?)
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/number")
            val expectedResponse = HttpResponse(200, "")
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `should be able to mock out an object matching the dictionary type in the request`() {
        val contractGherkin = """Feature: Contract for /store API
  Scenario Outline: api call
    When POST /variables
    And request-body (string: number)
    Then status 200
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/variables").updateBody(JSONObjectValue(mapOf("one" to NumberValue(1), "two" to NumberValue(2))))
            val expectedResponse = HttpResponse.EMPTY_200
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
            val restTemplate = RestTemplate()
            try {
                val response = restTemplate.postForEntity<String>(URI.create("${mock.baseURL}/variables"), """{"one": 1, "two": 2}""")
                assertThat(response.statusCode.value()).isEqualTo(200)
            } catch (e: HttpClientErrorException) {
                fail("Throw exception: ${e.localizedMessage}")
            }
        }
    }

    @Test
    fun `should be able to mock out an object matching the dictionary type in the response`() {
        val contractGherkin = """Feature: Contract for /store API
  Scenario Outline: api call
    When GET /variables
    Then status 200
    And response-body (string: number)
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/variables")
            val expectedResponse = HttpResponse(200, """{"one": 1, "two": 2}""")
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
            val restTemplate = RestTemplate()
            try {
                val response = restTemplate.getForEntity<String>(URI.create("${mock.baseURL}/variables"))
                assertThat(response.statusCode.value()).isEqualTo(200)
                val responseBody = parsedJSON(response.body ?: "")
                if(responseBody !is JSONObjectValue) fail("Expected JSONObjectValue")

                assertThat(responseBody.jsonObject.getValue("one")).isEqualTo(NumberValue(1))
                assertThat(responseBody.jsonObject.getValue("two")).isEqualTo(NumberValue(2))

            } catch (e: HttpClientErrorException) {
                fail("Throw exception: ${e.localizedMessage}")
            }
        }
    }

    @Test
    fun `should be able to mock out number in string in request and response`() {
        val contractGherkin = """Feature: Contract for /store API
  Scenario Outline: api call
    When POST /variables
    And request-body
      | number | (number in string) |
    Then status 200
    And response-body
      | number | (number in string) |
""".trimIndent()

        ContractMock.fromGherkin(contractGherkin).use { mock ->
            mock.start()
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/variables").updateBody("""{"number": "10"}""")
            val expectedResponse = HttpResponse(200, """{"number": "20"}""")
            mock.createMockScenario(MockScenario(expectedRequest, expectedResponse))
            val restTemplate = RestTemplate()
            try {
                val response = restTemplate.postForEntity<String>(URI.create("${mock.baseURL}/variables"), """{"number": "10"}""")
                assertThat(response.statusCode.value()).isEqualTo(200)
                val responseBody = parsedJSON(response.body ?: "")
                if(responseBody !is JSONObjectValue) fail("Expected json object")

                assertThat(responseBody.jsonObject.getValue("number")).isEqualTo(StringValue("20"))
            } catch (e: HttpClientErrorException) {
                fail("Throw exception: ${e.localizedMessage}")
            }
        }
    }
}
