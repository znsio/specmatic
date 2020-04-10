package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import run.qontract.core.pattern.NumberTypePattern
import run.qontract.test.TestExecutor
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import run.qontract.core.value.*
import java.util.*

class SetupFacts {
    @Test
    @Throws(Throwable::class)
    fun setupServerStateUsingJson() {
        val contractGherkin = "" +
                "Feature: Contract for /balance API" +
                "\n\n" +
                "  Scenario: api call\n\n" +
                "    Given fact {account_id: 54321}\n" +
                "    When GET /balance?account_id=54321\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: 10, messages_left: 20}" +
                ""
        setupServerStateTest(contractGherkin)
    }

    @Test
    @Throws(Throwable::class)
    fun setupServerStateUsingStatement() {
        val contractGherkin = "" +
                "Feature: Contract for /balance API" +
                "\n\n" +
                "  Scenario: api call\n\n" +
                "    Given fact account_id 54321\n" +
                "    When GET /balance?account_id=54321\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: 10, messages_left: 20}" +
                ""
        setupServerStateTest(contractGherkin)
    }

    @Throws(Throwable::class)
    private fun setupServerStateTest(contractGherkin: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val request = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account_id", "54321")
        contractBehaviour.setServerState(object : HashMap<String, Value>() {
            init {
                put("account_id", NumberValue(54321))
            }
        })
        val response = contractBehaviour.lookup(request)
        assertEquals(200, response.status)
        val responseJSON = JSONObject(response.body)
        assertEquals(responseJSON.getInt("calls_left"), 10)
        assertEquals(responseJSON.getInt("messages_left"), 20)
    }

    @Test
    @Throws(Throwable::class)
    fun contractTestShouldSetupExpectedServerState() {
        val contractGherkin = "" +
                "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    Given fact user jack\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"jack\", address: \"(string)\"}\n" +
                "    Then status 409\n" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverStateForValidation = HashMap<String, Any?>()
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("jack", serverStateForValidation["user"])
                val jsonBody = jsonObject(request.body)
                val name = jsonBody["name"]
                assertEquals("jack", name?.toStringValue())
                return HttpResponse(409, null, HashMap())
            }

            override fun setServerState(serverState: Map<String, Value>) {
                serverStateForValidation.putAll(serverState)
            }
        })
    }

    @Test
    @Throws(Throwable::class)
    fun `Contract should line up fixed integer id with json id`() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario: api call
    Given fact id 10
    When POST /accounts
    And request-body {"name": "jack", "id": "(number)", "address": "(string)"}
    Then status 200
"""

        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverStateForValidation = HashMap<String, Any?>()

        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals(10, serverStateForValidation["id"])
                val jsonBody = jsonObject(request.body)
                val id = jsonBody["id"] as NumberValue
                assertEquals(10, id.number)
                return HttpResponse(200, null, HashMap())
            }

            override fun setServerState(serverState: Map<String, Value>) {
                serverStateForValidation.putAll(serverState)
            }
        })
    }

    @Test
    @Throws(Throwable::class)
    fun `Contract should line up integer pattern id with json id`() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario: api call
    Given fact id (number)
    When POST /accounts
    And request-body {"name": "jack", "id": "(number)", "address": "(string)"}
    Then status 200
"""

        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverStateForValidation = HashMap<String, Value>()

        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertTrue(NumberTypePattern().matches(serverStateForValidation["id"], Resolver()) is Result.Success)
                val jsonBody = jsonObject(request.body)
                assertTrue(NumberTypePattern().matches(jsonBody["id"], Resolver()) is Result.Success)
                return HttpResponse(200, null, HashMap())
            }

            override fun setServerState(serverState: Map<String, Value>) {
                serverStateForValidation.putAll(serverState)
            }
        })
    }

    @Test
    @Throws(Throwable::class)
    fun theRightScenarioShouldBePickedUpBasedOnServerState() {
        val contractGherkin = "" +
                "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    Given fact user jack\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"jack\", address: \"(string)\"}\n" +
                "    Then status 409\n" +
                "\n\n" +
                "  Scenario: api call\n\n" +
                "    Given fact no_user\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"john\", address: \"(string)\"}\n" +
                "    Then status 200\n" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverStateForValidation = emptyMap<String, Value>().toMutableMap()
        val logs: MutableList<String> = mutableListOf()

        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return when {
                    serverStateForValidation.containsKey("user") -> {
                        serverStateForValidation["user"].let { user ->
                            if(user !is StringValue)
                                fail("Expected user to be a string, got $user")

                            assertEquals("jack", user.string)
                        }


                        request.body?.let {
                            if(it is JSONObjectValue) {
                                it.jsonObject.getValue("name").let { name ->
                                    if(name !is StringValue)
                                        fail("Expected name to be a string, got $name")

                                    assertEquals("jack", name.string)
                                }

                                logs.add("user")
                            }
                        }

                        HttpResponse(409, null, HashMap())
                    }
                    serverStateForValidation.containsKey("no_user") -> {
                        assertEquals(True, serverStateForValidation["no_user"])

                        request.body?.let {
                            if(it is JSONObjectValue) {
                                it.jsonObject.getValue("name").let { name ->
                                    if(name !is StringValue)
                                        fail("Expected string, but got $name")

                                    assertEquals("john", name.string)
                                }

                                logs.add("no_user")
                            }
                        }

                        HttpResponse(200, null, HashMap())
                    }
                    else -> {
                        HttpResponse(400, "Bad Request", HashMap())
                    }
                }
            }

            override fun setServerState(serverState: Map<String, Value>) {
                serverStateForValidation.clear()
                serverStateForValidation.putAll(serverState)
            }
        })

        val expectedLogs = listOf("user", "no_user")
        assertEquals(expectedLogs, logs.toList())
    }

    @Test
    @Throws(Throwable::class)
    fun matchUserIdGivenInSetupAgainstUserIdPassed() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    Given fact userid\n" +
                "    When GET /accounts?userid=(number)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/accounts").updateQueryParam("userid", "10")
        contractBehaviour.setServerState(object : HashMap<String, Value>() {
            init {
                put("userid", NumberValue(10))
            }
        })
        val httpResponse = contractBehaviour.lookup(httpRequest)
        assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body)
        assertThat(actual["calls_left"]).isInstanceOf(Number::class.java)
        assertThat(actual["messages_left"]).isInstanceOf(Number::class.java)
    }

    @Test
    @Throws(Throwable::class)
    fun factMatchesSpecifiedValueInJSONRequestBody() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n" +
                "    Given fact account_id\n" +
                "    When POST /account\n" +
                "    And request-body {\"account_id\": \"(number)\"}\n" +
                "    Then status 200\n" +
                "    And response-body {\"name\": \"(string)\"}" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverState: HashMap<String, Value> = object : HashMap<String, Value>() {
            init {
                put("account_id", NumberValue(10))
            }
        }
        contractBehaviour.setServerState(serverState)
        val request = HttpRequest().updateMethod("POST").updatePath("/account").updateBody("{\"account_id\": 10}")
        val response = contractBehaviour.lookup(request)
        assertNotNull(response)
        assertEquals(200, response.status)
        val jsonObject = JSONObject(response.body)
        assertThat(jsonObject["name"]).isInstanceOf(String::class.java)
    }

    @Test
    @Throws(Throwable::class)
    fun factMatchesSpecifiedValueInXMLRequestBody() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n" +
                "    Given fact account_id\n" +
                "    When POST /account\n" +
                "    And request-body <account_id>(number)</account_id>\n" +
                "    Then status 200\n" +
                "    And response-body {\"name\": \"(string)\"}" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverState: HashMap<String, Value> = object : HashMap<String, Value>() {
            init {
                put("account_id", NumberValue(10))
            }
        }
        contractBehaviour.setServerState(serverState)
        val request = HttpRequest().updateMethod("POST").updatePath("/account").updateBody("<account_id>10</account_id>")
        val response = contractBehaviour.lookup(request)
        assertNotNull(response)
        assertEquals(200, response.status)
        val jsonObject = JSONObject(response.body)
        assertThat(jsonObject["name"]).isInstanceOf(String::class.java)
    }

    @Test
    @Throws(Throwable::class)
    fun factMatchesSpecifiedValueInXMLRequestBodyInAttributes() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n" +
                "    Given fact account_id\n" +
                "    When POST /account\n" +
                "    And request-body <account account_id=\"(number)\">(string)</account>\n" +
                "    Then status 200\n" +
                "    And response-body {\"name\": \"(string)\"}" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverState: HashMap<String, Value> = object : HashMap<String, Value>() {
            init {
                put("account_id", NumberValue(10))
            }
        }
        contractBehaviour.setServerState(serverState)
        val request = HttpRequest().updateMethod("POST").updatePath("/account").updateBody("<account account_id=\"10\">(string)</account>")
        val response = contractBehaviour.lookup(request)
        assertNotNull(response)
        assertEquals(200, response.status)
        val jsonObject = JSONObject(response.body)
        assertThat(jsonObject["name"]).isInstanceOf(String::class.java)
    }
}