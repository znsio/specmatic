package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertThat(httpResponse.status).isEqualTo(200)
        val responseBodyJSON = JSONObject(httpResponse.body)
        val expectedBodyJSON = JSONObject(expectedBody)
        assertThat(responseBodyJSON.getInt("calls_left")).isEqualTo(expectedBodyJSON.getInt("calls_left"))
        assertThat(responseBodyJSON.getInt("messages_left")).isEqualTo(expectedBodyJSON.getInt("messages_left"))
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request on unmatched path in url`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario:
                    When GET /balance?account-id=(number)
                    Then status 200
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance2").updateQueryParam("account-id", "10")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body).isEqualTo("""
            This request did not match any scenario.
            Scenario: GET /balance?account-id=(number) Error:
            	URL did not match
            	Path part did not match. Expected: balance Actual: balance2
            	Request: HttpRequest(method=GET, path=/balance2, headers={}, body=, queryParams={account-id=10}, formFields={})

        """.trimIndent())
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request when request headers do not match`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario:
                    When GET /balance
                    And request-header x-loginId (string)
                    Then status 200
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateHeader("y-loginId", "abc123")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body).isEqualTo("""
            This request did not match any scenario.
            Scenario: GET /balance Error:
            	Request Headers did not match
            	Header "x-loginId" was not available
            	Request: HttpRequest(method=GET, path=/balance, headers={y-loginId=abc123}, body=, queryParams={}, formFields={})

        """.trimIndent())
    }

    @Test
    @Throws(Throwable::class)
    fun `should generate response headers`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario:
                    When GET /balance
                    Then status 200
                    And response-header token test
                    And response-header length (number)
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertThat(httpResponse.status).isEqualTo(200)
        assertThat(httpResponse.headers["token"]).isEqualTo("test")
        assertThat(httpResponse.headers["length"]).matches("[0-9]+")
    }

    @Test
    @Throws(Throwable::class)
    fun `should match integer token in query params`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario:
                    When GET /balance?account-id=(number)
                    Then status 200
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account-id", "10")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertThat(httpResponse.status).isEqualTo(200)
        val responseBodyJSON = JSONObject(httpResponse.body)
        assertThat(responseBodyJSON.getInt("calls_left")).isEqualTo(10)
        assertThat(responseBodyJSON.getInt("messages_left")).isEqualTo(30)
    }

    @Test
    @Throws(Throwable::class)
    fun `should match JSON array in request`() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When POST /balance\n" +
                "    And request-body {calls_made: [3, 10, \"(number)\"]}\n" +
                "    Then status 200"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/balance").updateBody("{calls_made: [3, 10, 2]}")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertEquals(200, httpResponse.status)
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request on unmatched JSON array in request`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario:
                    When POST /balance
                    And request-body {calls_made: [3, 10, 2]}
                    Then status 200
                """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/balance").updateBody("{calls_made: [3, 10]}")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body).isEqualTo("""This request did not match any scenario.
Scenario: POST /balance Error:
	Request body did not match
	Expected value at calls_made to match JSONArrayPattern(pattern=[ExactMatchPattern(pattern=3), ExactMatchPattern(pattern=10), ExactMatchPattern(pattern=2)]), actual value [3,10] in JSONObject {calls_made=[3,10]}
	JSON Array did not match Expected: [ExactMatchPattern(pattern=3), ExactMatchPattern(pattern=10), ExactMatchPattern(pattern=2)] Actual: [3, 10]
	Request: HttpRequest(method=POST, path=/balance, headers={}, body={"calls_made":[3,10]}, queryParams={}, formFields={})
""")
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request on wrong type in JSON array in request`() {
        val contractGherkin = """
                Feature: Contract for /balance API
                  Scenario:
                    When POST /balance
                    And request-body {calls_made: [3, 10, "(number)"]}
                    Then status 200
                """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/balance").updateBody("{calls_made: [3, 10, \"test\"]}")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body).isEqualTo("""This request did not match any scenario.
Scenario: POST /balance Error:
	Request body did not match
	Expected value at calls_made to match JSONArrayPattern(pattern=[ExactMatchPattern(pattern=3), ExactMatchPattern(pattern=10), LookupPattern(pattern=(number), key=null)]), actual value [3,10,"test"] in JSONObject {calls_made=[3,10,"test"]}
	Expected value at index 2 to match (number). Actual value: test in [3, 10, test]
	test should be a Number
	Request: HttpRequest(method=POST, path=/balance, headers={}, body={"calls_made":[3,10,"test"]}, queryParams={}, formFields={})
""")
    }

    @Test
    @Throws(Throwable::class)
    fun floatRecognisedInQueryParams() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When GET /balance?account-id=(number)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: 10, messages_left: 30}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account-id", "10.1")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val responseBodyJSON = JSONObject(httpResponse.body)
        assertEquals(10, responseBodyJSON.getInt("calls_left"))
        assertEquals(30, responseBodyJSON.getInt("messages_left"))
    }

    @Test
    @Throws(Throwable::class)
    fun `should return bad request when Integer token does not match in query params`() {
        val contractGherkin = """
                Feature: Contract for /balance API\
                  Scenario: 
                    When GET /balance?account-id=(number)
                    Then status 200
                    And response-body {calls_left: 10, messages_left: 30}
                """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updatePath("/balance").updateQueryParam("account-id", "abc").updateMethod("GET")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertThat(httpResponse.status).isEqualTo(400)
        assertThat(httpResponse.body).isEqualTo("""This request did not match any scenario.
Scenario: GET /balance?account-id=(number) Error:
	URL did not match
	Query parameter did not match
	Expected (number), actual abc
	"abc" is not a Number
	Request: HttpRequest(method=GET, path=/balance, headers={}, body=, queryParams={account-id=abc}, formFields={})
""")
    }

    @Test
    @Throws(Throwable::class)
    fun matchStringTokenInQueryParams() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When GET /balance?account-id=(string)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: 10, messages_left: 30}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updatePath("/balance").updateQueryParam("account-id", "abc").updateMethod("GET")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val responseBody = JSONObject(httpResponse.body)
        assertEquals(10, responseBody.getInt("calls_left"))
        assertEquals(30, responseBody.getInt("messages_left"))
    }

    @Test
    @Throws(Throwable::class)
    fun matchPOSTJSONBody() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario: 

    When POST /accounts
    And request-body {name: "(string)", address: "(string)"}
    Then status 200
    And response-body {calls_left: "(number)", messages_left: "(number)"}"""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/accounts").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body)
        assertNotNull(httpResponse)
        assertTrue(actual["calls_left"] is Int)
        assertTrue(actual["messages_left"] is Int)
    }

    @Test
    @Throws(Throwable::class)
    fun matchMultipleScenarios() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When GET /balance?id=(number)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}\n\n" +
                "  Scenario: \n\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"(string)\", address: \"(string)\"}\n" +
                "    Then status 200\n"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        var httpRequest: HttpRequest
        var httpResponse: HttpResponse
        val jsonResponse: JSONObject
        httpRequest = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("id", "100")
        httpResponse = contractBehaviour.lookup(httpRequest)
        jsonResponse = JSONObject(httpResponse.body)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue(jsonResponse["calls_left"] is Int)
        assertTrue(jsonResponse["messages_left"] is Int)
        httpRequest = HttpRequest().updateMethod("POST").updatePath("/accounts").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")
        httpResponse = contractBehaviour.lookup(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue(httpResponse.body!!.isEmpty())
    }

    @Test
    @Throws(Throwable::class)
    fun scenarioMatchesWhenFactIsSetupWithoutFixtureData() {
        val contractGherkin = """Feature: Contract for /locations API
  Scenario Outline: 
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
        contractBehaviour.setServerState(object : HashMap<String, Any>() {
            init {
                put("cities_exist", true)
            }
        })
        httpResponse = contractBehaviour.lookup(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val responseBody = JSONObject(httpResponse.body)
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
                "  Scenario Outline: \n\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"(string)\", address: \"(string)\"}\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/accounts").updateBody("{name: \"Holmes\", address: \"221 Baker Street\"}")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        val actual = JSONObject(httpResponse.body)
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
            
              Scenario:
                When POST /accounts1
                And request-body (Person)
                Then status 200
                And response-body (Info)
            
              Scenario:
                When POST /accounts2
                And request-body (Person)
                Then status 200
                And response-body (Info)
            """

        val contractBehaviour = ContractBehaviour(contractGherkin)

        val test = { httpRequest: HttpRequest ->
            val httpResponse = contractBehaviour.lookup(httpRequest)
            assertEquals(200, httpResponse.status)
            val actual = JSONObject(httpResponse.body)
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
            
              Scenario Outline: 
            
                Given fact id 10
                When GET /accounts/(id:number)
                Then status 200
                And response-body {"name": "(string)"}"""

        val contractBehaviour = ContractBehaviour(contractGherkin)

        contractBehaviour.setServerState(mutableMapOf<String, Any>().apply {
            this["id"] = true
        })

        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/accounts/10")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body)
        assertTrue(actual.has("name"))
        assertNotNull(actual.get("name"))
    }

    @Test
    fun `Contract Fake state can be setup using a concrete value`() {
        val contractGherkin = """
            Feature: Contract for /balance API
            
              Scenario: 
                Given fact id 10
                When GET /accounts/(id:number)
                Then status 200
                And response-body {"name": "(string)"}"""

        val contractBehaviour = ContractBehaviour(contractGherkin)

        contractBehaviour.setServerState(mutableMapOf<String, Any>().apply {
            this["id"] = 10
        })

        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/accounts/10")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body)
        assertTrue(actual.has("name"))
        assertNotNull(actual.get("name"))
    }

    @Test
    fun `Contract should be able to represent a number in the request and response body`() {
        val contractGherkin = """
            Feature: Contract for /account API
            
              Scenario: 
                When POST /account
                  And request-body (number)
                Then status 200
                  And response-body (number)"""

        val contractBehaviour = ContractBehaviour(contractGherkin)

        val httpRequest = HttpRequest().updateMethod("POST").updatePath("/account").updateBody("10")
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        assertTrue(NumericStringPattern().matches(asValue(httpResponse.body), Resolver()) is Result.Success)
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
        val httpResponse = behaviour.lookup(httpRequest)

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
        val httpResponse = behaviour.lookup(httpRequest)

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
        val httpResponse = behaviour.lookup(httpRequest)

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
        val httpResponse = behaviour.lookup(httpRequest)

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
        val httpResponse = behaviour.lookup(httpRequest)

        assertEquals(200, httpResponse.status)
        val array = parsedJSON(httpResponse.body ?: "{}")

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
        val httpResponse = behaviour.lookup(httpRequest)

        assertEquals(200, httpResponse.status)
        val jsonObject = parsedJSON(httpResponse.body ?: "{}")

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
        val httpResponse = behaviour.lookup(httpRequest)

        assertEquals(200, httpResponse.status)
        val jsonObject = parsedJSON(httpResponse.body ?: "{}")

        assertTrue(jsonObject is JSONObjectValue)
        assertTrue(if(jsonObject is JSONObjectValue) {
            val ids = jsonObject.jsonObject["ids"] ?: NoValue()
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
        val requestPattern = HttpRequestPattern(HttpHeadersPattern(), null, null, NoContentPattern(), mapOf("Data" to NumberTypePattern()))
        val request = HttpRequest().copy(formFields = mapOf("Data" to "10"))
        assertTrue(requestPattern.matchFormFields(request to Resolver()) is MatchSuccess)
    }

    @Test
    fun `returns error for form fields`() {
        val requestPattern = HttpRequestPattern(HttpHeadersPattern(), null, null, NoContentPattern(), mapOf("Data" to NumberTypePattern()))
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
        val httpResponse = behaviour.lookup(httpRequest)

        assertEquals(200, httpResponse.status)
        assertTrue(NumericStringPattern().parse(httpResponse.body ?: "", Resolver()) is NumberValue)
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
        val httpResponse = behaviour.lookup(httpRequest)

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
        val httpResponse = behaviour.lookup(httpRequest)

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
        val httpResponse = behaviour.lookup(httpRequest)

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
        val httpResponse = behaviour.lookup(httpRequest)

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
        val httpResponse = behaviour.lookup(httpRequest)

        assertEquals(200, httpResponse.status)
    }

    companion object {
        @JvmStatic
        private fun singleFeatureContractSource(): Stream<Arguments> {
            val featureData = arrayOf(
                    "Feature: Contract for /balance API\n\n" +
                            "  Scenario: \n\n" +
                            "    When GET /balance?account-id=10\n" +
                            "    Then status 200\n" +
                            "    And response-body {calls_left: 10, messages_left: 20}",
                    "Feature: Contract for /balance API\n\n" +
                            "  Scenario: \n\n" +
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