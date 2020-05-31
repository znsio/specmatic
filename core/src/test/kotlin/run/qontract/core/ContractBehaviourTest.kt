package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import run.qontract.core.pattern.*
import run.qontract.core.value.*
import java.util.*
import java.util.stream.Stream

class ContractBehaviourTest {
    @DisplayName("Single Feature Contract")
    @ParameterizedTest
    @MethodSource("singleFeatureContractSource")
    @Throws(Throwable::class)
    fun `should lookup a single feature contract`(gherkinData: String?, accountId: String?, expectedBody: String?) {
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account-id", accountId ?: "")
        val contractBehaviour = ContractBehaviour(gherkinData!!)
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(200)
        val responseBodyJSON = JSONObject(httpResponse.body?.displayableValue())
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance2").updateQueryParam("account-id", "10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body?.toStringValue()).isEqualTo("""In scenario "Get account balance"
>> REQUEST.URL.PATH (/balance2)

Expected string: "balance", actual was string: "balance2"""")
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateHeader("y-loginId", "abc123")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body?.toStringValue()).isEqualTo("""In scenario "Get balance info"
>> REQUEST.HEADERS

Expected header x-loginId was missing""".trimIndent())
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account-id", "10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(200)
        val responseBodyJSON = JSONObject(httpResponse.body?.displayableValue())
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/balance").updateBody("{calls_made: [3, 10]}")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body?.toStringValue()).isEqualTo("""In scenario "Update balance"
>> REQUEST.BODY.calls_made

Expected an array of length 3, actual length 2""")
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/balance").updateBody("{calls_made: [3, 10, \"test\"]}")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body?.toStringValue()).isEqualTo("""In scenario "Update balance"
>> REQUEST.BODY.calls_made.[2]

Expected number, actual was string: "test"""")
    }

    @Test
    @Throws(Throwable::class)
    fun floatRecognisedInQueryParams() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    When GET /balance?account-id=(number)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: 10, messages_left: 30}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account-id", "10.1")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val responseBodyJSON = JSONObject(httpResponse.body?.displayableValue())
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updatePath("/balance").updateQueryParam("account-id", "abc").updateMethod("GET")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body?.toStringValue()).isEqualTo("""In scenario "Get account balance"
>> REQUEST.URL.QUERY-PARAMS.account-id

Expected number, actual was "abc"""")
    }

    @Test
    @Throws(Throwable::class)
    fun matchStringTokenInQueryParams() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    When GET /balance?account-id=(string)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: 10, messages_left: 30}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updatePath("/balance").updateQueryParam("account-id", "abc").updateMethod("GET")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val responseBody = JSONObject(httpResponse.body?.displayableValue())
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/accounts").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body?.displayableValue())
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
        var httpRequest: HttpRequest
        var httpResponse: HttpResponse
        val jsonResponse: JSONObject
        httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("id", "100")
        httpResponse = contractBehaviour.lookupResponse(httpRequest)
        jsonResponse = JSONObject(httpResponse.body?.displayableValue())
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue(jsonResponse["calls_left"] is Int)
        assertTrue(jsonResponse["messages_left"] is Int)
        httpRequest = HttpRequest().updateMethod("POST").updatePath("/accounts").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")
        httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue(httpResponse.body?.toStringValue()?.isEmpty() ?: false)
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
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
        val responseBody = JSONObject(httpResponse.body?.displayableValue())
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
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/accounts").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        val actual = JSONObject(httpResponse.body?.displayableValue())
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

        val contractBehaviour = ContractBehaviour(contractGherkin)

        val test = { httpRequest: HttpRequest ->
            val httpResponse = contractBehaviour.lookupResponse(httpRequest)
            assertEquals(200, httpResponse.status)
            val actual = JSONObject(httpResponse.body?.displayableValue())
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

        val contractBehaviour = ContractBehaviour(contractGherkin)

        contractBehaviour.setServerState(mutableMapOf<String, Value>().apply {
            this["id"] = True
        })

        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/accounts/10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body?.displayableValue())
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

        val contractBehaviour = ContractBehaviour(contractGherkin)

        contractBehaviour.setServerState(mutableMapOf<String, Value>().apply {
            this["id"] = NumberValue(10)
        })

        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/accounts/10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body?.displayableValue())
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

        val contractBehaviour = ContractBehaviour(contractGherkin)

        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/account").updateBody("10")
        val httpResponse = contractBehaviour.lookupResponse(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue(NumberPattern.matches(httpResponse.body?.let { NumberValue(it.toStringValue().toInt()) } ?: EmptyString, Resolver()) is Result.Success)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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
        val requestPattern = HttpRequestPattern(HttpHeadersPattern(), null, null, NoContentPattern, mapOf("Data" to NumberPattern))
        val request = HttpRequest().copy(formFields = mapOf("Data" to "10"))
        assertTrue(requestPattern.matchFormFields(request to Resolver()) is MatchSuccess)
    }

    @Test
    fun `returns error for form fields`() {
        val requestPattern = HttpRequestPattern(HttpHeadersPattern(), null, null, NoContentPattern, mapOf("Data" to NumberPattern))
        val request = HttpRequest().copy(formFields = mapOf("Data" to "hello"))
        assertTrue(requestPattern.matchFormFields(request to Resolver()) is MatchFailure)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest(method="POST", path="/product", formFields=mapOf("number" to """{"val1": 10, "val2": 20}"""))
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `Contract fake should match a json object matching a dictionary pattern`() {
        val contractGherkin = """
Feature: Order API

Scenario: Order products
Given json Quantities (string: number)
When POST /order
    And request-body (Quantities)
Then status 200
""".trim()

        val behaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest(method="POST", path="/order", body = JSONObjectValue(mapOf("soap" to NumberValue(2), "toothpaste" to NumberValue(3))))
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    @Test
    fun `Contract fake should generate a json object matching a dictionary pattern`() {
        val contractGherkin = """
Feature: Order API

Scenario: Order products
Given json Quantities (string: number)
When GET /order
Then status 200
    And response-body (Quantities)
""".trim()

        val behaviour = ContractBehaviour(contractGherkin)
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

        val behaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest(method="GET", path="/number")
        val httpResponse = behaviour.lookupResponse(httpRequest)

        assertEquals(200, httpResponse.status)
        val json = httpResponse.body

        if(json !is JSONObjectValue) fail("Expected json object")

        assertThat(json.jsonObject.getValue("number")).isInstanceOf(StringValue::class.java)
        assertDoesNotThrow {
            json.jsonObject.getValue("number").toStringValue().toInt()
        }
    }

    companion object {
        @JvmStatic
        private fun singleFeatureContractSource(): Stream<Arguments> {
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