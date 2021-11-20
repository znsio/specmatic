package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.NumberPattern
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.utilities.parseXML
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.mock.NoMatchingScenario
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpStub
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.*
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import org.springframework.web.client.postForEntity
import org.w3c.dom.Node
import java.net.URI
import java.util.*

class HttpStubTests {
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
        queryParamJsonContract to HttpRequest().updateMethod("GET").updatePath("/balance_json")
            .updateQueryParam("userid", "10"),
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
        queryParameterXmlContract to HttpRequest().updateMethod("GET").updatePath("/balance_xml")
            .updateQueryParam("userid", "10"),
        pathParametersXmlContract to HttpRequest().updateMethod("GET").updatePath("/balance_xml/10")
    ).map { (contractGherkinString, httpRequest) ->
        DynamicTest.dynamicTest("when url is ${httpRequest.getURL("")}") {
            val expectedResponse =
                HttpResponse.xmlResponse("<balance><calls_left>100</calls_left><sms_messages_left>200</sms_messages_left></balance>")
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
        HttpStub(queryParamJsonContract).use { stub ->
            val expectedRequest =
                HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10")
            val expectedResponse = HttpResponse.jsonResponse("{call-mins-left: 100, smses-left: 200}")
            Assertions.assertThrows(NoMatchingScenario::class.java) {
                stub.createStub(
                    ScenarioStub(
                        expectedRequest,
                        expectedResponse
                    )
                )
            }
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should validate expectations and serve generated json`() {
        val expectedRequest =
            HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10")
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
        val expectedRequest =
            HttpRequest().updateMethod("GET").updatePath("/balance_json").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.jsonResponse("{call-mins-left: \"(number)\", sms-messages-left: 200}")
            .let { it.copy(headers = it.headers.plus("token" to "test")) }
        val response = validateAndRespond(
            """
                Feature: Contract for the balance service
                  Scenario: JSON API to get the balance for an individual
                    When GET /balance_json?userid=(number)
                    Then status 200
                    And response-body {"call-mins-left": "(number)", "sms-messages-left": "(number)"}
                    And response-header token test
                    And response-header Content-Type application/json
        """, expectedRequest, expectedResponse
        )
        val jsonResponse = JSONObject(Objects.requireNonNull(response.body))
        assertThat(response.statusCodeValue).isEqualTo(200)
        assertThat(jsonResponse.get("call-mins-left")).isInstanceOf(Number::class.java)
        assertThat(jsonResponse["sms-messages-left"]).isEqualTo(200)
        assertThat(response.headers["token"]).contains("test")
    }

    @Test
    @Throws(Throwable::class)
    fun `contract mock should validate expectations and serve generated xml`() {
        val expectedRequest =
            HttpRequest().updateMethod("GET").updatePath("/balance_xml").updateQueryParam("userid", "10")
        val expectedResponse =
            HttpResponse.xmlResponse("<balance><calls_left>100</calls_left><sms_messages_left>10</sms_messages_left></balance>")
        val response = validateAndRespond(queryParameterXmlContract, expectedRequest, expectedResponse)

        response.body?.let {
            val xmlResponse = parseXML(it)
            val root: Node = xmlResponse.documentElement
            Assertions.assertEquals("balance", root.nodeName)
            Assertions.assertEquals("sms_messages_left", root.lastChild.nodeName)
            Assertions.assertTrue(
                NumberPattern().matches(
                    NumberValue(root.firstChild.lastChild.nodeValue.toInt()),
                    Resolver()
                ) is Result.Success
            )
        } ?: fail("Expected body in the response")
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock server state`() {
        val expectedRequest =
            HttpRequest().updateMethod("GET").updatePath("/account_json").updateQueryParam("userid", "10")
        val expectedResponse = HttpResponse.jsonResponse("{\"name\": \"John Doe\"}")
        val response = validateAndRespond(contractGherkin, expectedRequest, expectedResponse)
        val jsonResponse = JSONObject(Objects.requireNonNull(response.body))
        Assertions.assertEquals(200, response.statusCodeValue)
        assertThat(jsonResponse["name"]).isInstanceOf(String::class.java)
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock multi valued arrays in request body`() {
        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/locations")
            val responseBody =
                "{\"locations\": [{\"id\": 123, \"name\": \"Mumbai\"}, {\"id\": 123, \"name\": \"Mumbai\"}]}"
            val expectedResponse = HttpResponse.jsonResponse(responseBody)
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock multi valued arrays using pattern in request body`() {
        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/special_locations")
            val responseBody =
                "{\"locations\": [{\"id\": 123, \"name\": \"Mumbai\"}, {\"id\": 123, \"name\": \"Mumbai\"}]}"
            val expectedResponse = HttpResponse.jsonResponse(responseBody)
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
        }
    }

    @Test
    @Throws(Throwable::class)
    fun `contract should mock multi valued arrays using pattern in response body`() {
        HttpStub(contractGherkin).use { mock ->
            val requestBody =
                "{\"locations\": [{\"id\": 123, \"name\": \"Mumbai\"}, {\"id\": 123, \"name\": \"Mumbai\"}]}"
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/locations").updateBody(requestBody)
            val expectedResponse =
                HttpResponse.OK.let { it.copy(headers = it.headers.plus("Content-Type" to "application/json")) }
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
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
        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/locations")
            val expectedResponse =
                HttpResponse(200, "{\"cities\":[{\"city\": \"Mumbai\"}, {\"city\": \"Bangalore\"}] }")
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
        }
    }

    @Throws(Throwable::class)
    private fun validateAndRespond(
        contractGherkinString: String,
        httpRequest: HttpRequest,
        httpResponse: HttpResponse
    ): ResponseEntity<String> {
        HttpStub(contractGherkinString).use { mock ->
            mock.createStub(ScenarioStub(httpRequest, httpResponse))
            val restTemplate = RestTemplate()
            return restTemplate.exchange(
                URI.create(httpRequest.getURL("http://localhost:9000")),
                HttpMethod.GET,
                null,
                String::class.java
            )
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

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/number").updateBody("10")
            val expectedResponse = HttpResponse(200, "10")
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `contract mock should be able to match enum in request and response bodies`() {
        val contractGherkin = """
          Feature: Contract for employees API
            Scenario: api call
              Given enum EmployeeType (string) values contract,permanent,trainee
              And enum Rating (number) values 1,2,3
              And enum Organisation (string) values hr,tech,admin
              And pattern Employee
              | name   | (string)       |
              | id     | (number)       |
              | type   | (EmployeeType) |
              | rating | (Rating?)       |
              When GET /(organisation:Organisation)/employees/?empType=(EmployeeType)
              Then status 200
              And response-body (Employee*)
""".trimIndent()

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/tech/employees?empType=contract")
            val expectedResponse = HttpResponse(200, """[{name: "emp1", id: 1, type: "contract", rating: null}]""")
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `contract mock should be able to match strings and numbers with restrictions`() {
        val contractGherkin = """
          Feature: Contract for /employees API
            Scenario: api call
              Given type EmployeeName (string) minLength 6 maxLength 12
              And type EmployeeId (number) minLength 8 maxLength 11
              And type Employee
              | name   | (EmployeeName) |
              | id     | (EmployeeId)   |
              When GET /employees
              Then status 200
              And response-body (Employee*)
""".trimIndent()

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/employees")
            val expectedResponse = HttpResponse(200, """[{name: "123123123", id: 123123123}]""")
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `contract mock should not allow nullable enum datatype`() {
        val contractGherkin = """
          Feature: Contract for /number API
            Scenario: api call
              And enum NumberType (string?) values real,imaginary
              And pattern Number
              | numberType | (NumberType) |
              When GET /numbers
              Then status 200
""".trimIndent()

        val exception = assertThrows<ContractException>({ "Should throw Contract Exception" }) {
            HttpStub(contractGherkin)
        }
        assertThat(exception.message).isEqualTo("Enums NumberType type (string?) cannot be nullable. To mark the enum nullable please use it with nullable syntax. Suggested Usage: (NumberType?)")
    }

    @Test
    fun `contract mock should generate error messages on enum in request and response bodies`() {
        val contractGherkin = """
          Feature: Contract for /number API
            Scenario: api call
              Given enum EmployeeType (string) values contract,permanent,trainee
              And enum Rating (number) values 1,2,3
              And enum Organisation (string) values hr,tech,admin
              And pattern Employee
              | name   | (string)       |
              | id     | (number)       |
              | type   | (EmployeeType) |
              | rating | (Rating)       |
              When GET /(organisation:Organisation)/employees/?empType=(EmployeeType)
              Then status 200
              And response-body (Employee*)
""".trimIndent()

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/tech/employees?empType=contract")
            val expectedResponse = HttpResponse(200, """[{name: "emp1", id: 1, type: "contract", rating: 4}]""")
            try {
                mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
                throw AssertionError("Should not allow unexpected values in enums")
            } catch (e: Exception) {
                assertThat(e.toString()).isEqualTo(
                    """
                    in.specmatic.mock.NoMatchingScenario: In scenario "api call"
                    API: GET /(organisation:Organisation)/employees/ -> 200
                    
                      >> RESPONSE.BODY.[0].rating
                      
                      Expected (1 or 2 or 3), Actual was number: 4
                """.trimIndent()
                )
            }
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

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/number").updateBody(NumberValue(10))
            val expectedResponse = HttpResponse.OK
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
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

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/number").updateBody(NullValue)
            val expectedResponse = HttpResponse.OK
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
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

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/number")
            val expectedResponse = HttpResponse(200, "10")
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
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

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/number")
            val expectedResponse = HttpResponse(200, "")
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
        }
    }

    @Test
    fun `should be able to mock out an object matching the dictionary type in the request`() {
        val contractGherkin = """Feature: Contract for /store API
  Scenario Outline: api call
    When POST /variables
    And request-body (dictionary string number)
    Then status 200
""".trimIndent()

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("POST").updatePath("/variables")
                .updateBody(JSONObjectValue(mapOf("one" to NumberValue(1), "two" to NumberValue(2))))
            val expectedResponse = HttpResponse.OK
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
            val restTemplate = RestTemplate()
            try {
                val response = restTemplate.postForEntity<String>(
                    URI.create("${mock.endPoint}/variables"),
                    """{"one": 1, "two": 2}"""
                )
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
    And response-body (dictionary string number)
""".trimIndent()

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest().updateMethod("GET").updatePath("/variables")
            val expectedResponse = HttpResponse(200, """{"one": 1, "two": 2}""")
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
            val restTemplate = RestTemplate()
            try {
                val response = restTemplate.getForEntity<String>(URI.create("${mock.endPoint}/variables"))
                assertThat(response.statusCode.value()).isEqualTo(200)
                val responseBody = parsedJSON(response.body ?: "")
                if (responseBody !is JSONObjectValue) fail("Expected JSONObjectValue")

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

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest =
                HttpRequest().updateMethod("POST").updatePath("/variables").updateBody("""{"number": "10"}""")
            val expectedResponse = HttpResponse(200, """{"number": "20"}""")
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))
            val restTemplate = RestTemplate()
            try {
                val response =
                    restTemplate.postForEntity<String>(URI.create("${mock.endPoint}/variables"), """{"number": "10"}""")
                assertThat(response.statusCode.value()).isEqualTo(200)
                val responseBody = parsedJSON(response.body ?: "")
                if (responseBody !is JSONObjectValue) fail("Expected json object")

                assertThat(responseBody.jsonObject.getValue("number")).isEqualTo(StringValue("20"))
            } catch (e: HttpClientErrorException) {
                fail("Throw exception: ${e.localizedMessage}")
            }
        }
    }

    @Test
    fun `should be able to stub out form fields in the request`() {
        val contractGherkin = """Feature: Contract for /store API
  Scenario Outline: api call
    When POST /variables
    And form-field Data (number)
    Then status 200
""".trimIndent()

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest =
                HttpRequest().updateMethod("POST").updatePath("/variables").copy(formFields = mapOf("Data" to "10"))

            val expectedResponse = HttpResponse.OK
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))

            try {
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

                val map: MultiValueMap<String, String> = LinkedMultiValueMap()
                map.add("Data", "10")

                val request: HttpEntity<MultiValueMap<String, String>> = HttpEntity(map, headers)

                val response = RestTemplate().postForEntity<String>(URI.create("${mock.endPoint}/variables"), request)
                assertThat(response.statusCode.value()).isEqualTo(200)
            } catch (e: HttpClientErrorException) {
                fail("Threw exception: ${e.localizedMessage}")
            }
        }
    }

    @Test
    fun `should be able to stub out a json request body`() {
        val contractGherkin = """Feature: Contract for /store API
  Scenario Outline: api call
    When POST /variables
    And request-body
      | name | (string) |
      | age  | (number) |
    Then status 200
""".trimIndent()

        HttpStub(contractGherkin).use { mock ->
            val expectedRequest = HttpRequest(
                method = "POST",
                path = "/variables",
                body = parsedValue("""{"name": "John Doe", "age": 10}""")
            )

            val expectedResponse = HttpResponse.OK
            mock.createStub(ScenarioStub(expectedRequest, expectedResponse))

            try {
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                val request: HttpEntity<String> = HttpEntity("""{"name": "John Doe", "age": 10}""", headers)

                val response = RestTemplate().postForEntity<String>(URI.create("${mock.endPoint}/variables"), request)
                assertThat(response.statusCode.value()).isEqualTo(200)
            } catch (e: HttpClientErrorException) {
                fail("Threw exception: ${e.localizedMessage}")
            }
        }
    }
}
