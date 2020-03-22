package run.qontract.core

import run.qontract.core.pattern.NumberTypePattern
import run.qontract.core.pattern.StringPattern
import run.qontract.core.pattern.asValue
import run.qontract.test.TestExecutor
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class SetupServerState {
    @Test
    @Throws(Throwable::class)
    fun setupServerStateUsingJson() {
        val contractGherkin = "" +
                "Feature: Contract for /balance API" +
                "\n\n" +
                "  Scenario: \n\n" +
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
                "  Scenario: \n\n" +
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
        contractBehaviour.setServerState(object : HashMap<String, Any>() {
            init {
                put("account_id", 54321)
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
                "  Scenario: \n\n" +
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
                val jsonBody = request.body!!.value as Map<String, Any?>
                assertEquals("jack", jsonBody["name"])
                return HttpResponse(409, null, HashMap())
            }

            override fun setServerState(serverState: Map<String, Any?>) {
                serverStateForValidation.putAll(serverState)
            }
        })
    }

    @Test
    @Throws(Throwable::class)
    fun `Contract should line up fixed integer id with json id`() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario: 
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
                val jsonBody = request.body!!.value as Map<String, Any?>
                assertEquals(10, jsonBody["id"])
                return HttpResponse(200, null, HashMap())
            }

            override fun setServerState(serverState: Map<String, Any?>) {
                serverStateForValidation.putAll(serverState)
            }
        })
    }

    @Test
    @Throws(Throwable::class)
    fun `Contract should line up integer pattern id with json id`() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario: 
    Given fact id (number)
    When POST /accounts
    And request-body {"name": "jack", "id": "(number)", "address": "(string)"}
    Then status 200
"""

        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverStateForValidation = HashMap<String, Any?>()

        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertTrue(NumberTypePattern().matches(asValue(serverStateForValidation["id"]), Resolver()) is Result.Success)
                val jsonBody = asValue(request.body).value as Map<String, Any?>
                assertTrue(NumberTypePattern().matches(asValue(jsonBody["id"]), Resolver()) is Result.Success)
                return HttpResponse(200, null, HashMap())
            }

            override fun setServerState(serverState: Map<String, Any?>) {
                serverStateForValidation.putAll(serverState)
            }
        })
    }

    @Test
    @Throws(Throwable::class)
    fun theRightScenarioShouldBePickedUpBasedOnServerState() {
        val contractGherkin = "" +
                "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    Given fact user jack\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"jack\", address: \"(string)\"}\n" +
                "    Then status 409\n" +
                "\n\n" +
                "  Scenario: \n\n" +
                "    Given fact no_user\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"john\", address: \"(string)\"}\n" +
                "    Then status 200\n" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverStateForValidation = HashMap<String, Any?>()
        val logs: MutableList<String> = ArrayList()
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return if (serverStateForValidation.containsKey("user")) {
                    assertEquals("jack", serverStateForValidation["user"])
                    val jsonBody = request.body!!.value as Map<String, Any?>
                    assertEquals("jack", jsonBody["name"])
                    logs.add("user")
                    HttpResponse(409, null, HashMap())
                } else if (serverStateForValidation.containsKey("no_user")) {
                    assertEquals(true, serverStateForValidation["no_user"])
                    val jsonBody = request.body!!.value as Map<String, Any?>
                    assertEquals("john", jsonBody["name"])
                    logs.add("no_user")
                    HttpResponse(200, null, HashMap())
                } else {
                    HttpResponse(400, "Bad Request", HashMap())
                }
            }

            override fun setServerState(serverState: Map<String, Any?>) {
                serverStateForValidation.clear()
                serverStateForValidation.putAll(serverState)
            }
        })
        val expectedLogs: List<String> = object : ArrayList<String>() {
            init {
                add("user")
                add("no_user")
            }
        }
        assertEquals(expectedLogs, logs)
    }

    @Test
    @Throws(Throwable::class)
    fun matchUserIdGivenInSetupAgainstUserIdPassed() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    Given fact userid\n" +
                "    When GET /accounts?userid=(number)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpRequest = HttpRequest().updateMethod("GET").updatePath("/accounts").updateQueryParam("userid", "10")
        contractBehaviour.setServerState(object : HashMap<String, Any>() {
            init {
                put("userid", 10)
            }
        })
        val httpResponse = contractBehaviour.lookup(httpRequest)
        Assertions.assertNotNull(httpResponse)
        assertEquals(200, httpResponse.status)
        val actual = JSONObject(httpResponse.body)
        assertTrue(NumberTypePattern().matches(asValue(actual["calls_left"]), Resolver()) is Result.Success)
        assertTrue(NumberTypePattern().matches(asValue(actual["messages_left"]), Resolver()) is Result.Success)
    }

    @Test
    @Throws(Throwable::class)
    fun factMatchesSpecifiedValueInJSONRequestBody() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n" +
                "    Given fact account_id\n" +
                "    When POST /account\n" +
                "    And request-body {\"account_id\": \"(number)\"}\n" +
                "    Then status 200\n" +
                "    And response-body {\"name\": \"(string)\"}" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverState: HashMap<String, Any> = object : HashMap<String, Any>() {
            init {
                put("account_id", 10)
            }
        }
        contractBehaviour.setServerState(serverState)
        val request = HttpRequest().updateMethod("POST").updatePath("/account").updateBody("{\"account_id\": 10}")
        val response = contractBehaviour.lookup(request)
        Assertions.assertNotNull(response)
        assertEquals(200, response.status)
        val jsonObject = JSONObject(response.body)
        assertTrue(StringPattern().matches(asValue(jsonObject["name"]), Resolver()) is Result.Success)
    }

    @Test
    @Throws(Throwable::class)
    fun factMatchesSpecifiedValueInXMLRequestBody() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n" +
                "    Given fact account_id\n" +
                "    When POST /account\n" +
                "    And request-body <account_id>(number)</account_id>\n" +
                "    Then status 200\n" +
                "    And response-body {\"name\": \"(string)\"}" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverState: HashMap<String, Any> = object : HashMap<String, Any>() {
            init {
                put("account_id", 10)
            }
        }
        contractBehaviour.setServerState(serverState)
        val request = HttpRequest().updateMethod("POST").updatePath("/account").updateBody("<account_id>10</account_id>")
        val response = contractBehaviour.lookup(request)
        Assertions.assertNotNull(response)
        assertEquals(200, response.status)
        val jsonObject = JSONObject(response.body)
        assertTrue(StringPattern().matches(asValue(jsonObject["name"]), Resolver()) is Result.Success)
    }

    @Test
    @Throws(Throwable::class)
    fun factMatchesSpecifiedValueInXMLRequestBodyInAttributes() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n" +
                "    Given fact account_id\n" +
                "    When POST /account\n" +
                "    And request-body <account account_id=\"(number)\">(string)</account>\n" +
                "    Then status 200\n" +
                "    And response-body {\"name\": \"(string)\"}" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val serverState: HashMap<String, Any> = object : HashMap<String, Any>() {
            init {
                put("account_id", 10)
            }
        }
        contractBehaviour.setServerState(serverState)
        val request = HttpRequest().updateMethod("POST").updatePath("/account").updateBody("<account account_id=\"10\">(string)</account>")
        val response = contractBehaviour.lookup(request)
        Assertions.assertNotNull(response)
        assertEquals(200, response.status)
        val jsonObject = JSONObject(response.body)
        assertTrue(StringPattern().matches(asValue(jsonObject["name"]), Resolver()) is Result.Success)
    }
}