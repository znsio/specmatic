package `in`.specmatic.core

import `in`.specmatic.conversions.OpenApiSpecification
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import `in`.specmatic.core.pattern.EmptyStringPattern
import `in`.specmatic.core.pattern.NumberPattern
import `in`.specmatic.core.value.*
import java.util.*
import java.util.stream.Stream

class FeatureTest {
    @Test
    fun `test output should contain example name`() {
val contract = OpenApiSpecification.fromYAML("""
openapi: 3.0.0
info:
  title: Sample Product API
  description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
  version: 0.1.9
servers:
  - url: http://localhost:8080
    description: Local
paths:
  /products:
    post:
      summary: Add Product
      description: Add Product
      requestBody:
        content:
          application/json:
            examples:
              SUCCESS:
                value:
                  name: abc
                  sku: "123"
            schema:
              type: object
              required:
                - name
                - sku
              properties:
                name:
                  type: string
                sku:
                  type: string
      responses:
        '200':
          description: Returns Product With Id
          content:
            application/json:
              examples:
                SUCCESS:
                  value:
                    id: 10
              schema:
                type: object
                required:
                  - id
                properties:
                  id:
                    type: integer
""".trimIndent(), "").toFeature()

        val scenario: Scenario = contract.generateContractTestScenarios(emptyList()).first()
        assertThat(scenario.testDescription()).contains("SUCCESS | ")
    }

    @Test
    fun `test output should contain example name and preserve WIP tag`() {
        val contract = OpenApiSpecification.fromYAML("""
openapi: 3.0.0
info:
  title: Sample Product API
  description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
  version: 0.1.9
servers:
  - url: http://localhost:8080
    description: Local
paths:
  /products:
    post:
      summary: Add Product
      description: Add Product
      requestBody:
        content:
          application/json:
            examples:
              "[WIP] SUCCESS":
                value:
                  name: abc
                  sku: "123"
            schema:
              type: object
              required:
                - name
                - sku
              properties:
                name:
                  type: string
                sku:
                  type: string
      responses:
        '200':
          description: Returns Product With Id
          content:
            application/json:
              examples:
                "[WIP] SUCCESS":
                  value:
                    id: 10
              schema:
                type: object
                required:
                  - id
                properties:
                  id:
                    type: integer
""".trimIndent(), "").toFeature()

        val scenario: Scenario = contract.generateContractTestScenarios(emptyList()).first()
        assertThat(scenario.testDescription()).contains("[WIP] SUCCESS | ")
    }
    @DisplayName("Single Feature Contract")
    @ParameterizedTest
    @MethodSource("singleFeatureContractSource")
    @Throws(Throwable::class)
    fun `should lookup a single feature contract`(gherkinData: String?, accountId: String?, expectedBody: String?) {
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account-id", accountId ?: "")
        val contractBehaviour = parseGherkinStringToFeature(gherkinData!!)
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(200)
        val responseBodyJSON = JSONObject(httpResponse.body.displayableValue())
        val expectedBodyJSON = JSONObject(expectedBody)
        assertThat(responseBodyJSON.getInt("calls_left")).isEqualTo(expectedBodyJSON.getInt("calls_left"))
        assertThat(responseBodyJSON.getInt("messages_left")).isEqualTo(expectedBodyJSON.getInt("messages_left"))
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request on unmatched path in url`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario: Get account balance
                    When GET /balance?account-id=(number)
                    Then status 200
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance2").updateQueryParam("account-id", "10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body.toStringLiteral()).isEqualTo("No matching REST stub or contract found for method GET and path /balance2 (assuming you're looking for a REST API since no SOAPAction header was detected)")
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request when request headers do not match`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario: Get balance info
                    When GET /balance
                    And request-header x-loginId (string)
                    Then status 200
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateHeader("y-loginId", "abc123")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body.toStringLiteral()).isEqualTo(
            """
            In scenario "Get balance info"
            API: GET /balance -> 200
            
              >> REQUEST.HEADERS.x-loginId
              
                 Expected header named "x-loginId" was missing
              """.trimIndent())
    }

    @Test
    @Throws(Throwable::class)
    fun `should generate response headers`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario: api call
                    When GET /balance
                    Then status 200
                    And response-header token test
                    And response-header length (number)
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(200)
        assertThat(httpResponse.headers["token"]).isEqualTo("test")
        assertThat(httpResponse.headers["length"]).matches("[0-9]+")
    }

    @Test
    @Throws(Throwable::class)
    fun `should match integer token in query params`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario: api call
                    When GET /balance?account-id=(number)
                    Then status 200
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account-id", "10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(200)
        val responseBodyJSON = JSONObject(httpResponse.body.displayableValue())
        assertThat(responseBodyJSON.getInt("calls_left")).isEqualTo(10)
        assertThat(responseBodyJSON.getInt("messages_left")).isEqualTo(30)
    }

    @Test
    @Throws(Throwable::class)
    fun `should match JSON array in request`() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    When POST /balance\n" +
                "    And request-body {calls_made: [3, 10, \"(number)\"]}\n" +
                "    Then status 200"
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/balance").updateBody("{calls_made: [3, 10, 2]}")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertEquals(200, httpResponse.status)
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request on unmatched JSON array in request`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario: Update balance
                    When POST /balance
                    And request-body {calls_made: [3, 10, 2]}
                    Then status 200
                """
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/balance").updateBody("{calls_made: [3, 10]}")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body.toStringLiteral()).isEqualTo(
            """
            In scenario "Update balance"
            API: POST /balance -> 200
            
              >> REQUEST.BODY.calls_made
              
                 Expected an array of length 3, actual length 2
            """.trimIndent())
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request on wrong type in JSON array in request`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario: Update balance
                    When POST /balance
                    And request-body {"calls_made": [3, 10, "(number)"]}
                    Then status 200
                """
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/balance").updateBody("{calls_made: [3, 10, \"test\"]}")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body.toStringLiteral()).isEqualTo(
            """
            In scenario "Update balance"
            API: POST /balance -> 200
            
              >> REQUEST.BODY.calls_made[2]
              
                 Expected number, actual was "test"
            """.trimIndent())
    }

    @Test
    @Throws(Throwable::class)
    fun floatRecognisedInQueryParams() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    When GET /balance?account-id=(number)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: 10, messages_left: 30}"
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account-id", "10.1")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val responseBodyJSON = JSONObject(httpResponse.body.displayableValue())
        assertEquals(10, responseBodyJSON.getInt("calls_left"))
        assertEquals(30, responseBodyJSON.getInt("messages_left"))
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request when Integer token does not match in query params`() {
        val contractGherkin = """
                Feature: Contract for /balance API\
                  Scenario: Get account balance
                    When GET /balance?account-id=(number)
                    Then status 200
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updatePath("/balance").updateQueryParam("account-id", "abc").updateMethod("GET")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body.toStringLiteral()).isEqualTo(
            """
            In scenario "Get account balance"
            API: GET /balance -> 200
            
              >> REQUEST.QUERY-PARAMS.account-id
              
                 Expected number, actual was "abc"
            """.trimIndent())
    }

    @Test
    @Throws(Throwable::class)
    fun matchStringTokenInQueryParams() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    When GET /balance?account-id=(string)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: 10, messages_left: 30}"
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updatePath("/balance").updateQueryParam("account-id", "abc").updateMethod("GET")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val responseBody = JSONObject(httpResponse.body.displayableValue())
        assertEquals(10, responseBody.getInt("calls_left"))
        assertEquals(30, responseBody.getInt("messages_left"))
    }

    @Test
    @Throws(Throwable::class)
    fun matchPOSTJSONBody() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario: api call

    When POST /accounts
    And request-body {name: "(string)", address: "(string)"}
    Then status 200
    And response-body {calls_left: "(number)", messages_left: "(number)"}"""
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/accounts").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body.displayableValue())
        assertNotNull(httpResponse)
        assertTrue(actual["calls_left"] is Int)
        assertTrue(actual["messages_left"] is Int)
    }

    @Test
    @Throws(Throwable::class)
    fun matchMultipleScenarios() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    When GET /balance?id=(number)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}\n\n" +
                "  Scenario: api call\n\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"(string)\", address: \"(string)\"}\n" +
                "    Then status 200\n"
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        var httpRequest: HttpRequest
        var httpResponse: HttpResponse
        val jsonResponse: JSONObject
        httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("id", "100")
        httpResponse = contractBehaviour.lookupResponse(httpRequest)
        jsonResponse = JSONObject(httpResponse.body.displayableValue())
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue(jsonResponse["calls_left"] is Int)
        assertTrue(jsonResponse["messages_left"] is Int)
        httpRequest = HttpRequest().updateMethod("POST").updatePath("/accounts").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")
        httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue(httpResponse.body.toStringLiteral().isEmpty())
    }

    @Test
    @Throws(Throwable::class)
    fun scenarioMatchesWhenFactIsSetupWithoutFixtureData() {
        val contractGherkin = """Feature: Contract for /locations API
  Scenario Outline: api call
    * fixture city_list {"cities": [{"city": "Mumbai"}, {"city": "Bangalore"}]}
    * pattern City {"city": "(string)"}
    * pattern Cities {"cities": ["(City...)"]}
    Given fact cities_exist 
    When GET /locations
    Then status 200
    And response-body (Cities)
  Examples:
  | cities_exist | 
  | city_list | 
    """
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpResponse: HttpResponse
        val httpRequest: HttpRequest = HttpRequest().updateMethod("GET").updatePath("/locations")
        contractBehaviour.setServerState(object : HashMap<String, Value>() {
            init {
                put("cities_exist", True)
            }
        })
        httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val responseBody = JSONObject(httpResponse.body.displayableValue())
        val cities = responseBody.getJSONArray("cities")
        for (i in 0 until cities.length()) {
            val city = cities.getJSONObject(i)
            assertTrue(city.getString("city").length > 0)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun scenarioOutlineRunsLikeAnEmptyScenario() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario Outline: api call\n\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"(string)\", address: \"(string)\"}\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}"
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/accounts").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        val actual = JSONObject(httpResponse.body.displayableValue())
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue(actual["calls_left"] is Int)
        assertTrue(actual["messages_left"] is Int)
    }

    @Test
    @Throws(Throwable::class)
    fun `Background reflects in multiple scenarios`() {
        val contractGherkin = """
            Feature: Contract for /balance API
            
              Background:
                * pattern Person {name: "(string)", address: "(string)"}
                * pattern Info {calls_left: "(number)", messages_left: "(number)"}
            
              Scenario: api call
                When POST /accounts1
                And request-body (Person)
                Then status 200
                And response-body (Info)
            
              Scenario: api call
                When POST /accounts2
                And request-body (Person)
                Then status 200
                And response-body (Info)
            """

        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

        val test = { httpRequest: HttpRequest ->
            val httpResponse = contractBehaviour.lookupResponse(httpRequest)
            assertEquals(200, httpResponse.status)
            val actual = JSONObject(httpResponse.body.displayableValue())
            assertTrue(actual["calls_left"] is Int)
            assertTrue(actual["messages_left"] is Int)
        }

        val baseRequest = HttpRequest().updateMethod("POST").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")

        for (path in listOf("/accounts1", "/accounts2")) {
            test(baseRequest.updatePath(path))
        }
    }

    @Test
    fun `Contract Fake state can be setup using true without specifying a concrete value`() {
        val contractGherkin = """
            Feature: Contract for /balance API
            
              Scenario Outline: api call
            
                Given fact id 10
                When GET /accounts/(id:number)
                Then status 200
                And response-body {"name": "(string)"}"""

        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

        contractBehaviour.setServerState(mutableMapOf<String, Value>().apply {
            this["id"] = True
        })

        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/accounts/10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body.displayableValue())
        assertTrue(actual.has("name"))
        assertNotNull(actual.get("name"))
    }

    @Test
    fun `Contract Fake state can be setup using a concrete value`() {
        val contractGherkin = """
            Feature: Contract for /balance API
            
              Scenario: api call
                Given fact id 10
                When GET /accounts/(id:number)
                Then status 200
                And response-body {"name": "(string)"}"""

        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

        contractBehaviour.setServerState(mutableMapOf<String, Value>().apply {
            this["id"] = NumberValue(10)
        })

        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/accounts/10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body.displayableValue())
        assertTrue(actual.has("name"))
        assertNotNull(actual.get("name"))
    }

    @Test
    fun `Contract should be able to represent a number in the request and response body`() {
        val contractGherkin = """
            Feature: Contract for /account API
            
              Scenario: api call
                When POST /account
                  And request-body (number)
                Then status 200
                  And response-body (number)"""

        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/account").updateBody("10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue( NumberPattern().matches(NumberValue(httpResponse.body.toStringLiteral().toInt()), Resolver()) is Result.Success)
    }

    @Test
    fun `Contract should parse tabular data and match a json object against it`() {
        val contractGherkin = """
            Feature: Contract for /user API
            
            Background:
            Given pattern User
            | id   | (number) |
            | name | (string) |
            
            Scenario: update user info
            When POST /user
                And request-body (User)
            Then status 200
        """.trimIndent()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/user").updateBody("""{"id": 10, "name": "John Doe"}""")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `Contract should match an array of json objects specified in the request`() {
        val contractGherkin = """
            Feature: Contract for /user API
            
            Background:
            Given pattern User
            | id   | (number) |
            | name | (string) |
            
            Scenario: update info of multiple users
            When POST /user
                And request-body (User*)
            Then status 200
        """.trimIndent()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/user").updateBody("""[{"id": 10, "name": "John Doe"}, {"id": 20, "name": "Jane Doe"}]""")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `Contract should match an array of json objects specified as a pattern`() {
        val contractGherkin = """
            Feature: Contract for /user API
            
            Background:
            Given pattern User
            | id   | (number) |
            | name | (string) |
            And pattern Users (User*)
            
            Scenario: update info of multiple users
            When POST /user
                And request-body (Users)
            Then status 200
        """.trimIndent()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/user").updateBody("""[{"id": 10, "name": "John Doe"}, {"id": 20, "name": "Jane Doe"}]""")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `Contract should generate a fake json response from a tabular pattern`() {
        val contractGherkin = """
            Feature: Contract for /user API
            
            Background:
            Given pattern User
            | id   | (number) |
            | name | (string) |
            
            Scenario: Get info about a user
            When GET /user/(id:number)
            Then status 200
                And response-body (User)
        """.trimIndent()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/user/10")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `Contract should generate a fake json list response from a tabular pattern`() {
        val contractGherkin = """
            Feature: Contract for /user API
            
            Background:
            Given pattern User
            | id   | (number) |
            | name | (string) |
            
            Scenario: Get info about a user
            When GET /user
            Then status 200
                And response-body (User*)
        """.trimIndent()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/user")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
        val array = httpResponse.body

        assertTrue(array is JSONArrayValue)

        if(array is JSONArrayValue) {
            for(value in array.list) {
                assertTrue(value is JSONObjectValue)

                if(value is JSONObjectValue) {
                    assertTrue(value.jsonObject.getValue("id") is NumberValue)
                    assertTrue(value.jsonObject.getValue("name") is StringValue)
                }
            }
        }
    }

    @Test
    fun `Contract should generate a fake json response for a list as part of a json object`() {
        val contractGherkin = """
            Feature: Contract for /user API
            
            Background:
            Given pattern UserData
            | ids   | (number*) |
            
            Scenario: Get info about a user
            When GET /userdata
            Then status 200
                And response-body (UserData)
        """.trimIndent()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/userdata")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
        val jsonObject = httpResponse.body

        assertTrue(jsonObject is JSONObjectValue)
        if(jsonObject is JSONObjectValue) {
            val ids = jsonObject.jsonObject.getValue("ids")
            assert(ids is JSONArrayValue)

            if(ids is JSONArrayValue) {
                for (value in ids.list) {
                    assertTrue(value is NumberValue)
                }
            }
        }
    }

    @Test
    fun `json keyword acts like pattern but reads tabular syntax`() {
        val contractGherkin = """
            Feature: Contract for /user API
            
            Background:
            Given json UserData
            | ids   | (number*) |
            
            Scenario: Get info about a user
            When GET /userdata
            Then status 200
                And response-body (UserData)
        """.trimIndent()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/userdata")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
        val jsonObject = httpResponse.body

        assertTrue(jsonObject is JSONObjectValue)
        assertTrue(if(jsonObject is JSONObjectValue) {
            val ids = jsonObject.jsonObject["ids"] ?: EmptyString
            assertTrue(ids is JSONArrayValue)

            if(ids is JSONArrayValue) {
                for (value in ids.list) {
                    assertTrue(value is NumberValue)
                }

                true
            } else false
        } else false)
    }

    @Test
    fun `successfully matches valid form fields`() {
        val requestPattern = HttpRequestPattern(HttpHeadersPattern(), null, null, EmptyStringPattern, mapOf("Data" to NumberPattern()))
        val request = HttpRequest().copy(formFields = mapOf("Data" to "10"))
        assertTrue(requestPattern.matchFormFields(Triple(request, Resolver(), emptyList())) is MatchSuccess)
    }

    @Test
    fun `returns error for form fields`() {
        val requestPattern = HttpRequestPattern(HttpHeadersPattern(), null, null, EmptyStringPattern, mapOf("Data" to NumberPattern()))
        val request = HttpRequest().copy(formFields = mapOf("Data" to "hello"))
        val result: MatchingResult<Triple<HttpRequest, Resolver, List<Result.Failure>>> = requestPattern.matchFormFields(Triple(request, Resolver(), emptyList()))
        result as MatchSuccess<Triple<HttpRequest, Resolver, List<Result.Failure>>>
        val failures = result.value.third
        assertThat(failures).hasSize(1)
    }

    @Test
    fun `form fields pattern are recognised and matched`() {
        val contractGherkin = """
Feature: Math API

Scenario: Square a number
When POST /squareof
    And form-field number (number)
Then status 200
    And response-body (number)
""".trim()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest(method="POST", path="/squareof", formFields=mapOf("number" to "10"))
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
        assertTrue(httpResponse.body is NumberValue)
    }

    @Test
    fun `form fields pattern errors are recognised and results in a 400 http error`() {
        val contractGherkin = """
Feature: Math API

Scenario: Square a number
When POST /squareof
    And form-field number (number)
Then status 200
    And response-body (number)
""".trim()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest(method="POST", path="/squareof", formFields=mapOf("number" to "hello"))
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(400, httpResponse.status)
    }

    @Test
    fun `incorrectly formatted json array passed in a form field returns a 400 error`() {
        val contractGherkin = """
Feature: Math API

Scenario: Product of a number
Given json Numbers ["(number)", "(number)"]
When POST /product
    And form-field number (Numbers)
Then status 200
    And response-body (number)
""".trim()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest(method="POST", path="/product", formFields=mapOf("number" to "[1]"))
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(400, httpResponse.status)
    }

    @Test
    fun `json array is recognised when passed in a form field`() {
        val contractGherkin = """
Feature: Math API

Scenario: Product of a number
Given json Numbers ["(number)", "(number)"]
When POST /product
    And form-field number (Numbers)
Then status 200
    And response-body (number)
""".trim()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest(method="POST", path="/product", formFields=mapOf("number" to "[1, 2]"))
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `json object is recognised when passed in a form field`() {
        val contractGherkin = """
Feature: Math API

Scenario: Product of a number
Given json Numbers
| val1 | (number) |
| val2 | (number) |
When POST /product
    And form-field number (Numbers)
Then status 200
    And response-body (number)
""".trim()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest(method="POST", path="/product", formFields=mapOf("number" to """{"val1": 10, "val2": 20}"""))
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `incorrectly formatted json object passed in a form field returns a 400 error`() {
        val contractGherkin = """
Feature: Math API

Scenario: Product of a number
Given json Numbers
| val1 | (number) |
| val2 | (number) |
When POST /product
    And form-field number (Numbers)
Then status 200
    And response-body (number)
""".trim()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest(method="POST", path="/product", formFields=mapOf("number" to """{"val1": 10, "val2": 20}"""))
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `Contract fake should match a json object matching a dictionary pattern`() {
        val contractGherkin = """
Feature: Order API

Scenario: Order products
Given json Quantities (dictionary string number)
When POST /order
    And request-body (Quantities)
Then status 200
""".trim()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest(method="POST", path="/order", body = JSONObjectValue(mapOf("soap" to NumberValue(2), "toothpaste" to NumberValue(3))))
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `Contract fake should generate a json object matching a dictionary pattern`() {
        val contractGherkin = """
Feature: Order API

Scenario: Order products
Given json Quantities (dictionary string number)
When GET /order
Then status 200
    And response-body (Quantities)
""".trim()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest(method="GET", path="/order")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
        val json = httpResponse.body

        if(json !is JSONObjectValue) fail("Expected JSONObjectValue")

        for((key, value) in json.jsonObject) {
            assertThat(key.length).isPositive()
            assertThat(value).isInstanceOf(NumberValue::class.java)
        }
    }

    @Test
    fun `should generate number in string`() {
        val contractGherkin = """
Feature: Number API

Scenario: Random number
When GET /number
Then status 200
And response-body
| number | (number in string) |
""".trim()

        val behaviour = parseGherkinStringToFeature(contractGherkin)
        val httpRequest = HttpRequest(method="GET", path="/number")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
        val json = httpResponse.body

        if(json !is JSONObjectValue) fail("Expected json object")

        assertThat(json.jsonObject.getValue("number")).isInstanceOf(StringValue::class.java)
        assertDoesNotThrow {
            json.jsonObject.getValue("number").toStringLiteral().toInt()
        }
    }

    private val threeQuotes = "\"\"\""

    @Test
    fun `basic xml scenario`() {
        val contractGherkin = """
Feature: Basic XML API

Scenario: Random number
When POST /number
And request-body
$threeQuotes
<request>
<number>(number)</number>
</request>
$threeQuotes
Then status 200
""".trim()

        val feature = parseGherkinStringToFeature(contractGherkin)

        fun test(request: HttpRequest) {
            try {
                val response = feature.lookupResponse(request)
                assertThat(response.status).isEqualTo(200).withFailMessage(response.toLogString())
            } catch(e: Throwable) {
                println(e.stackTrace)
            }
        }

        test(HttpRequest("POST", path = "/number", body = toXMLNode("""<request><number>10</number></request>""")))
        test(HttpRequest("POST", path = "/number", body = toXMLNode("""<request>
        <number>10</number> </request>""")
        ))
    }

    @Test
    fun `only one test is generated when all fields exist in the example and the generative flag is off`() {
        val contract = parseGherkinStringToFeature("""
            Feature: Test
                Background:
                    Given openapi openapi/three_keys_one_mandatory.yaml
                    
                Scenario: Test
                    When POST /data
                    Then status 200
                    
                    Examples:
                    | id | name   |
                    | 10 | Justin |
        """.trimIndent(), "src/test/resources/test.spec")

        val testScenarios: List<Scenario> = contract.generateContractTestScenarios(emptyList())
        assertThat(testScenarios).hasSize(1)
    }

    companion object {
        @JvmStatic
        fun singleFeatureContractSource(): Stream<Arguments> {
            val featureData = arrayOf(
                    "Feature: Contract for /balance API\n\n" +
                            "  Scenario: api call\n\n" +
                            "    When GET /balance?account-id=10\n" +
                            "    Then status 200\n" +
                            "    And response-body {calls_left: 10, messages_left: 20}",
                    "Feature: Contract for /balance API\n\n" +
                            "  Scenario: api call\n\n" +
                            "    When GET /balance?account-id=20\n" +
                            "    Then status 200\n" +
                            "    And response-body {calls_left: 10, messages_left: 30}"
            )
            return Stream.of(
                    Arguments.of(featureData[0], "10", "{calls_left: 10, messages_left: 20}"),
                    Arguments.of(featureData[1], "20", "{calls_left: 10, messages_left: 30}")
            )
        }
    }
}