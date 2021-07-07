package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.NumberPattern
import `in`.specmatic.core.pattern.StringPattern
import `in`.specmatic.core.value.*
import `in`.specmatic.test.TestExecutor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail
import java.util.*

class ContractAsTestWithSamplesInTable {
    @Test
    @Throws(Throwable::class)
    fun GETAndResponseBodyGeneratedThroughDataTableWithPathParams() {
        val contractGherkin = """Feature: Contract for /balance API

  Scenario Outline: api call
    When GET /balance/(account_id:number)
    Then status 200
    And response-body {calls_left: "(number)", messages_left: "(number)"}

  Examples:
  | account_id | calls_left | messages_left | 
  | 10 | 20 | 30 | 
  | hello | 30 | 40 | 
    """
        Assertions.assertThrows(ContractException::class.java) { jsonResponsesTestsShouldBeVerifiedAgainstTable(contractGherkin) }
    }

    @Throws(Throwable::class)
    private fun jsonResponsesTestsShouldBeVerifiedAgainstTable(contractGherkin: String) {
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val results = contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val accountId = request.queryParams.getOrElse("account_id") {
                    val pathParts = request.path!!.split("/".toRegex()).toTypedArray()
                    pathParts[pathParts.size - 1]
                }
                Assertions.assertEquals("GET", request.method)
                Assertions.assertTrue( NumberPattern().matches(NumberValue(accountId.toInt()), Resolver()) is Result.Success)
                val headers: HashMap<String, String> = object : HashMap<String, String>() {
                    init {
                        put("Content-Type", "application/json")
                    }
                }
                var jsonResponseString: String? = null
                if (accountId == "10") {
                    jsonResponseString = "{calls_left: 20, messages_left: 30}"
                } else if (accountId == "20") {
                    jsonResponseString = "{calls_left: 30, messages_left: \"hello\"}"
                }
                return HttpResponse(200, jsonResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Value>) {}
        })

        assertThat(results.success()).isTrue()
    }

    @Test
    @Throws(Throwable::class)
    fun POSTBodyAndResponseGeneratedThroughDataTable() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario Outline: api call
    When POST /account
    And request-body {"name": "(string)", "city": "(string)"}
    Then status 200
    And response-body {"account_id": "(number)"}

  Examples:
  | account_id | name | city | 
  | 10 | John Doe | Mumbai | 
  | 20 | Jane Doe | Bangalore | 
"""
        jsonRequestAndResponseTest(contractGherkin)
    }

    @Test
    @Throws(Throwable::class)
    fun `Examples in multiple tables should be used`() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario Outline: api call
    When POST /account
    And request-body {"name": "(string)", "city": "(string)"}
    Then status 200
    And response-body {"account_id": "(number)"}

  Examples:
  | account_id | name | city | 
  | 10 | John Doe | Mumbai | 
  
  Examples:
  | account_id | name | city | 
  | 20 | Jane Doe | Bangalore | 
"""
        jsonRequestAndResponseTest(contractGherkin)
    }

    @Test
    @Throws(Throwable::class)
    fun `Example values are picked up in the keys of json objects defined in lazy patterns`() {
        val contractGherkin = """
Feature: Contract for /balance API

  Scenario Outline: api call
    Given pattern Person {"name": "(string)", "address": "(Address)"}
    And pattern Address {"city": "(string)"}
    When POST /account
    And request-body (Person)
    Then status 200
    And response-body {"account_id": "(number)"}

  Examples:
  | account_id | name | city | 
  | 10 | John Doe | Mumbai | 
  | 20 | Jane Doe | Bangalore | 
"""

        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)

        val results = contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestJSON = jsonObject(request.body)
                val name = requestJSON["name"] as StringValue
                val city = (requestJSON["address"] as JSONObjectValue).jsonObject["city"] as StringValue
                Assertions.assertEquals("POST", request.method)
                Assertions.assertTrue(StringPattern().matches(city, Resolver()) is Result.Success)
                val headers: HashMap<String, String> = object : HashMap<String, String>() {
                    init {
                        put("Content-Type", "application/json")
                    }
                }

                if (name.string !in listOf("John Doe", "Jane Doe"))
                    throw Exception("Unexpected name $name")

                when (name.string) {
                    "John Doe" -> assertThat(city.string).isEqualTo("Mumbai")
                    "Jane Doe" -> assertThat(city.string).isEqualTo("Bangalore")
                }

                val jsonResponseString: String? = when (name.string) {
                    "John Doe" -> "{account_id: 10}"
                    else -> "{account_id: 20}"
                }

                return HttpResponse(200, jsonResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.success()).isTrue()
    }

    @Throws(Throwable::class)
    private fun jsonRequestAndResponseTest(contractGherkin: String) {
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val flags = mutableMapOf("john" to false, "jane" to false)

        val results = contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val requestJSON = request.body
                assert(requestJSON is JSONObjectValue)

                if (requestJSON is JSONObjectValue) {
                    val name = requestJSON.jsonObject.getValue("name") as StringValue

                    Assertions.assertEquals("POST", request.method)

                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }

                    var jsonResponseString: String? = null
                    if (name.string == "John Doe") {
                        flags["john"] = true
                        jsonResponseString = "{account_id: 10}"
                    } else if (name.string == "Jane Doe") {
                        flags["jane"] = true
                        jsonResponseString = "{account_id: 20}"
                    }
                    return HttpResponse(200, jsonResponseString, headers)
                } else {
                    return HttpResponse.ERROR_400
                }
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertTrue(flags["john"] ?: false)
        assertTrue(flags["jane"] ?: false)
        assertTrue(results.success(), results.report())
    }

    @Test
    @Throws(Throwable::class)
    fun POSTBodyAndResponseXMLGeneratedThroughDataTable() {
        val contractGherkin = """Feature: Contract for /balance API

  Scenario Outline: api call
    When POST /account
    And request-body <account><name>(string)</name><city>(string)</city></account>
    Then status 200
    And response-body <account><account_id>(number)</account_id></account>
  Examples: 
    | account_id | name | city | 
    | 10 | John Doe | Mumbai | 
    | 20 | Jane Doe | Bangalore | 
    """
        xmlRequestAndResponseTest(contractGherkin)
    }

    @Throws(Throwable::class)
    private fun xmlRequestAndResponseTest(contractGherkin: String) {
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val results = contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val root = (request.body as XMLNode)

                val nameItem = root.childNodes[0] as XMLNode
                val cityItem = root.childNodes[1] as XMLNode
                assertThat(nameItem.name).isEqualTo("name")
                assertThat(cityItem.name).isEqualTo("city")

                val name = nameItem.childNodes[0]

                assertThat(name).isInstanceOf(StringValue::class.java)
                assertThat(cityItem.childNodes[0]).isInstanceOf(StringValue::class.java)
                assertThat(request.method).isEqualTo("POST")

                val headers: HashMap<String, String> = object : HashMap<String, String>() {
                    init {
                        put("Content-Type", "application/xml")
                    }
                }

                val xmlResponseString: String = when(name.toStringLiteral()) {
                    "John Doe" -> "<account><account_id>10</account_id></account>"
                    "Jane Doe" -> "<account><account_id>20</account_id></account>"
                    else -> fail("Expected name to be either \"John Doe\" or \"Jane Doe\", got ${name.toStringLiteral()}")
                }

                return HttpResponse(200, xmlResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Value>) {}
        })

        assertTrue(results.success(), results.report())
    }
}
