package run.qontract.core

import run.qontract.core.HttpResponse.Companion.jsonResponse
import run.qontract.core.HttpResponse.Companion.xmlResponse
import run.qontract.core.pattern.NumberTypePattern
import run.qontract.core.pattern.NumericStringPattern
import run.qontract.core.pattern.StringPattern
import run.qontract.core.pattern.asValue
import run.qontract.core.utilities.parseXML
import run.qontract.fake.ContractFake
import run.qontract.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.SAXException
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException
import kotlin.collections.HashMap

class ContractAsTest {
    @Test
    @Throws(Throwable::class)
    fun runContractAsTest() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"(string)\", address: \"(string)\"}\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}"

        val flags = mutableMapOf<String, Boolean>()

        val contractBehaviour = ContractBehaviour(contractGherkin)
        val jsonResponseString = "{calls_left: 20, messages_left: 20}"

        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags["executed"] = true
                assertEquals("/accounts", request.path)
                val requestBody = request.body!!.value as Map<String, Any?>
                val name = requestBody["name"]
                val address = requestBody["address"]
                assertTrue(StringPattern().matches(asValue(name), Resolver()) is Result.Success)
                assertTrue(StringPattern().matches(asValue(address), Resolver()) is Result.Success)
                val headers: HashMap<String, String?> = object : HashMap<String, String?>() {
                    init {
                        put("Content-Type", "application/json")
                    }
                }
                return HttpResponse(200, jsonResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {
            }
        })

        assertThat(flags["executed"]).isTrue()
    }

    @Test
    @Throws(Throwable::class)
    fun `should verify response headers`() {
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
        val executionInfo = contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val headers: HashMap<String, String?> = hashMapOf(
                        "token" to "test",
                        "length" to "abc"
                )
                return HttpResponse(200, "{calls_left: 10, messages_left: 30}", headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
        assertThat(executionInfo.generateErrorMessage()).isEqualTo("""This request did not match any scenario.
Scenario: GET /balance Error:
	Response did not match
	Response headers did not match
	Header "length" did not match
	Expected: (number) Actual: abc
	"abc" is not a Number
	Request: HttpRequest(method=GET, path=/balance, headers={Content-Type=text/plain}, body=, queryParams={}, formFields={})
	Response: {"status":200,"body":"{calls_left: 10, messages_left: 30}","status-text":"OK","headers":{"length":"abc","token":"test"}}
""")
    }

    @Test
    @Throws(Throwable::class)
    fun `should generate request headers`() {
        val contractGherkin = """
            Feature: Contract for /balance API
                  Scenario:
                    When GET /balance
                    And request-header test (number)
                    Then status 200
        """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.headers.keys).contains("test")
                assertThat(request.headers["test"]).matches("[0-9]+")
                val headers: HashMap<String, String?> = HashMap()
                return HttpResponse(200, "{calls_left: 10, messages_left: 30}", headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun `should generate request headers with examples`() {
        val contractGherkin = """
            Feature: Contract for /balance API
                  Scenario:
                    When GET /balance
                    And request-header x-loginId (string)
                    Then status 200
                    Examples:
                    | x-loginId |
                    | a@b.com   |
        """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.headers.keys).contains("x-loginId")
                assertThat(request.headers["x-loginId"]).isEqualTo("a@b.com")
                val headers: HashMap<String, String?> = HashMap()
                return HttpResponse(200, "{calls_left: 10, messages_left: 30}", headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun `should be able to generate request body with example id number as string`() {
        val contractGherkin = """
            Feature: Contract for /balance API
                  Scenario:
                    When POST /balance
                    And request-body {"id": "(string)"}
                    Then status 200
                    Examples:
                    | id |
                    | 10 |
        """

        val flags = mutableMapOf<String, Boolean>()

        val contractBehaviour = ContractBehaviour(contractGherkin)

        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags["executed"] = true

                val body = request.body
                if (body !is JSONObjectValue)
                    throw Exception("Unexpected value type: ${request.body}")
                assertThat("10").isEqualTo(body.jsonObject["id"])
                val headers: HashMap<String, String?> = HashMap()
                return HttpResponse(200, "", headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) { }
        })

        assertThat(flags["executed"]).isTrue()
    }

    @Test
    fun `should substitute request parameters with suggestions`() {
        val contractGherkin = """
            Feature: Contract for /balance API
                  Scenario: scenario name
                    When GET /balance/(accountId:number)
                    And request-header x-loginId (string)
                    Then status 200
        """
        val parameters = """
            Feature: Contract for /balance API
                  Scenario: scenario name
                    Examples:
                    | x-loginId | accountId |
                    | a@b.com   | 123123    |
        """

        val flags = mutableMapOf<String, Boolean>()

        val contractBehaviour = ContractBehaviour(contractGherkin)
        val suggestions = lex(parseGherkinString(parameters))

        contractBehaviour.executeTests(suggestions, object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags["executed"] = true

                assertThat(request.getURL("")).isEqualTo("/balance/123123")
                assertThat(request.headers.keys).contains("x-loginId")
                assertThat(request.headers["x-loginId"]).isEqualTo("a@b.com")
                val headers: HashMap<String, String?> = HashMap()
                return HttpResponse(200, "{calls_left: 10, messages_left: 30}", headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })

        assertThat(flags["executed"]).isTrue()
    }

    @Test
    @Throws(Throwable::class)
    fun testShouldGenerateQueryParams() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When GET /accounts?account_id=(number)\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val jsonResponseString = "{calls_left: 20, messages_left: 20}"
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/accounts", request.path)
                assertTrue(NumericStringPattern()
                        .matches(asValue(request.queryParams["account_id"]), Resolver()) is Result.Success)
                val headers: HashMap<String, String?> = object : HashMap<String, String?>() {
                    init {
                        put("Content-Type", "application/json")
                    }
                }
                return HttpResponse(200, jsonResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun integerInsideJsonStringShouldNotMatchAsInteger() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"(string)\", address: \"(string)\"}\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val jsonResponseString = "{calls_left: 20, messages_left: \"20\"}"
        val executionInfo = contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/accounts", request.path)
                val jsonBody = request.body!!.value as Map<String, Any?>
                val name = jsonBody["name"]
                val address = jsonBody["address"]
                assertTrue(StringPattern().matches(asValue(name), Resolver()) is Result.Success)
                assertTrue(StringPattern().matches(asValue(address), Resolver()) is Result.Success)
                val headers: HashMap<String, String?> = object : HashMap<String, String?>() {
                    init {
                        put("Content-Type", "application/json")
                    }
                }

                return HttpResponse(200, jsonResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
        assertThat(executionInfo.unsuccessfulInteractionCount()).isEqualTo(1)
    }

    @Test
    @Throws(Throwable::class)
    fun floatRecognisedInJsonResponse() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"(string)\", address: \"(string)\"}\n" +
                "    Then status 200\n" +
                "    And response-body {calls_left: \"(number)\", messages_left: \"(number)\"}"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val jsonResponseString = "{calls_left: 20, messages_left: 20.1}"
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/accounts", request.path)
                val jsonBody = request.body!!.value as Map<String, Any?>
                val name = jsonBody["name"]
                val address = jsonBody["address"]
                assertTrue(StringPattern().matches(asValue(name), Resolver()) is Result.Success)
                assertTrue(StringPattern().matches(asValue(address), Resolver()) is Result.Success)
                val headers: HashMap<String, String?> = object : HashMap<String, String?>() {
                    init {
                        put("Content-Type", "application/json")
                    }
                }
                return HttpResponse(200, jsonResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun jsonArrayGeneratedInContractAsTest() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"(string)\", linked_ids: [1,2,3]}\n" +
                "    Then status 200"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/accounts", request.path)
                val jsonBody = request.body!!.value as Map<String, Any?>
                val name = jsonBody["name"]
                assertTrue(StringPattern().matches(asValue(name), Resolver()) is Result.Success)
                val expectedLinkedIds = arrayOf(1, 2, 3)
                val actualLinkedIds = jsonBody["linked_ids"] as List<Any?>
                for (i in expectedLinkedIds.indices) {
                    assertEquals(expectedLinkedIds[i], actualLinkedIds[i])
                }
                val headers = HashMap<String, String?>()
                return HttpResponse(200, "", headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun jsonObjectInJsonArrayGeneratedInContractAsTest() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"(string)\", linked_ids: [1,2,3, {a: \"(number)\", b: \"(string)\"}]}\n" +
                "    Then status 200"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/accounts", request.path)
                val jsonBody = request.body!!.value as Map<String, Any?>
                val name = jsonBody["name"]
                assertTrue(StringPattern().matches(asValue(name), Resolver()) is Result.Success)
                val expectedLinkedIds = arrayOf(1, 2, 3)
                val actualLinkedIds = jsonBody["linked_ids"] as List<Any?>
                for (i in expectedLinkedIds.indices) {
                    assertEquals(expectedLinkedIds[i], actualLinkedIds[i])
                }
                val innerObject = actualLinkedIds[3] as Map<String, Any?>
                assertTrue(NumberTypePattern().matches(asValue(innerObject["a"]), Resolver()) is Result.Success)
                assertTrue(StringPattern().matches(asValue(innerObject["b"]), Resolver()) is Result.Success)
                val headers = HashMap<String, String?>()
                return HttpResponse(200, "", headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun xmlMatchedInRequestAndReturnedInResponse() {
        val contractGherkin = "Feature: Contract for /balance API\n\n" +
                "  Scenario: \n\n" +
                "    When POST /accounts\n" +
                "    And request-body <account><name>John Doe</name><address>(string)</address></account>\n" +
                "    Then status 200\n" +
                "    And response-body <balance><calls_left>(number)</calls_left><messages_left>(number)</messages_left></balance>"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val xmlResponseString = "<balance><calls_left>20</calls_left><messages_left>20</messages_left></balance>"
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val document = request.body!!.value as Document
                val root: Node = document.documentElement
                val nameItem = root.childNodes.item(0)
                val addressItem = root.childNodes.item(1)
                assertEquals("name", nameItem.nodeName)
                assertEquals("address", addressItem.nodeName)
                assertEquals("John Doe", nameItem.firstChild.nodeValue)
                assertTrue(StringPattern().matches(asValue(addressItem.firstChild.nodeValue), Resolver()) is Result.Success)
                val headers: HashMap<String, String?> = object : HashMap<String, String?>() {
                    init {
                        put("Content-Type", "application/xml")
                    }
                }
                return HttpResponse(200, xmlResponseString, headers)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun dataInDatatablesPassedAsFactsDuringSetup() {
        val contractGherkin = """Feature: Contract for /balance API

  Scenario Outline: 
    Given fact userid
    When GET /accounts?userid=(number)
    Then status 200
    And response-body {"name": "(string)"}

  Examples:
  | userid | name |
  | 10       | jack    |
"""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/accounts", request.path)
                assertEquals("10", request.queryParams["userid"])
                return jsonResponse("{\"name\": \"jack\"}")
            }

            override fun setServerState(serverState: Map<String, Any?>) {
                assertEquals("10", serverState["userid"])
            }
        })
    }

    @Test
    @Throws(Throwable::class)
    fun jsonArrayGenerationInRequestBody() {
        val contractGherkin = "Feature: Contract for /locations API\n" +
                "  Scenario: \n" +
                "    Given pattern Location {\"city\": \"(string)\"}\n" +
                "    When POST /locations\n" +
                "    And request-body {\"locations\": [\"(Location*)\"]}\n" +
                "    Then status 200\n"
        verifyJsonArrayGenerationInRequestBody(contractGherkin)
    }

    @Test
    @Throws(Throwable::class)
    fun jsonArrayGenerationUsingPatternInRequestBody() {
        val contractGherkin = "Feature: Contract for /locations API\n" +
                "  Scenario: \n" +
                "    Given pattern Location {\"city\": \"(string)\"}\n" +
                "    And pattern Locations {\"locations\": [\"(Location*)\"]}\n" +
                "    When POST /locations\n" +
                "    And request-body (Locations)\n" +
                "    Then status 200\n"
        verifyJsonArrayGenerationInRequestBody(contractGherkin)
    }

    @Throws(Throwable::class)
    private fun verifyJsonArrayGenerationInRequestBody(contractGherkin: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/locations", request.path)
                assertEquals("POST", request.method)
                val requestBody = request.body!!.value as Map<String, Any?>
                val locations = requestBody["locations"] as List<Any?>
                for (i in locations.indices) {
                    val location = locations[i] as Map<String, Any?>
                    assertNotNull(location["city"])
                    assertTrue((location["city"] as String).length > 0)
                }
                return HttpResponse.EMPTY_200
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun jsonArrayGenerationInResponseBody() {
        val contractGherkin = "Feature: Contract for /locations API\n" +
                "  Scenario: \n" +
                "    Given pattern Location {\"city\": \"(string)\"}\n" +
                "    When GET /locations\n" +
                "    Then status 200\n" +
                "    And response-body {\"locations\": [\"(Location*)\"]}"
        verifyJsonArrayGenerationInResponseBody(contractGherkin)
    }

    @Test
    @Throws(Throwable::class)
    fun jsonArrayGenerationUsingPatternInResponseBody() {
        val contractGherkin = "Feature: Contract for /locations API\n" +
                "  Scenario: \n" +
                "    Given pattern Location {\"city\": \"(string)\"}\n" +
                "    And pattern Locations {\"locations\": [\"(Location*)\"]}\n" +
                "    When GET /locations\n" +
                "    Then status 200\n" +
                "    And response-body (Locations)"
        verifyJsonArrayGenerationInResponseBody(contractGherkin)
    }

    @Throws(Throwable::class)
    private fun verifyJsonArrayGenerationInResponseBody(contractGherkin: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/locations", request.path)
                assertEquals("GET", request.method)
                val jsonResponse = "{\"locations\": [{\"city\": \"Mumbai\"}, {\"city\": \"Bangalore\"}]}"
                return jsonResponse(jsonResponse)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun xmlArrayGenerationInRequestBody() {
        val contractGherkin = "Feature: Contract for /locations API\n" +
                "  Scenario: \n" +
                "    Given pattern Location <city>(string)</city>\n" +
                "    When POST /locations\n" +
                "    And request-body <locations>(Location*)</locations>\n" +
                "    Then status 200\n"
        verifyXMLArrayGenerationInRequestBody(contractGherkin)
    }

    @Test
    @Throws(Throwable::class)
    fun xmlArrayGenerationUsingPatternInRequestBody() {
        val contractGherkin = "Feature: Contract for /locations API\n" +
                "  Scenario: \n" +
                "    Given pattern Location <city>(string)</city>\n" +
                "    And pattern Locations <locations>(Location*)</locations>\n" +
                "    When POST /locations\n" +
                "    And request-body (Locations)\n" +
                "    Then status 200\n"
        verifyXMLArrayGenerationInRequestBody(contractGherkin)
    }

    @Throws(Throwable::class)
    private fun verifyXMLArrayGenerationInRequestBody(contractGherkin: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/locations", request.path)
                assertEquals("POST", request.method)
                try {
                    val document = parseXML(request.body.toString())
                    val root = document.documentElement
                    assertEquals("locations", root.nodeName)
                    val childNodes = root.childNodes
                    for (i in 0 until childNodes.length) {
                        val city = childNodes.item(i)
                        assertEquals("city", city.nodeName)
                        assertTrue(city.firstChild.nodeValue.length > 0)
                    }
                } catch (e: ParserConfigurationException) {
                    return HttpResponse.HTTP_400
                } catch (e: SAXException) {
                    return HttpResponse.HTTP_400
                } catch (e: IOException) {
                    return HttpResponse.HTTP_400
                }
                return HttpResponse.EMPTY_200
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun xmlArrayGenerationInResponseBody() {
        val contractGherkin = "Feature: Contract for /locations API\n" +
                "  Scenario: \n" +
                "    Given pattern Location <city>(string)</city>\n" +
                "    When GET /locations\n" +
                "    Then status 200\n" +
                "    And response-body <locations>(Location*)</locations>\n"
        verifyXMLArrayGenerationInResponseBody(contractGherkin)
    }

    @Test
    @Throws(Throwable::class)
    fun xmlArrayGenerationUsingPatternInResponseBody() {
        val contractGherkin = "Feature: Contract for /locations API\n" +
                "  Scenario: \n" +
                "    Given pattern Location <city>(string)</city>\n" +
                "    And pattern Locations <locations>(Location*)</locations>\n" +
                "    When GET /locations\n" +
                "    Then status 200\n" +
                "    And response-body (Locations)\n"
        verifyXMLArrayGenerationInResponseBody(contractGherkin)
    }

    @Throws(Throwable::class)
    private fun verifyXMLArrayGenerationInResponseBody(contractGherkin: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/locations", request.path)
                assertEquals("GET", request.method)
                val xmlResponse = "<locations><city>Mumbai</city><city>Bangalore</city></locations>"
                return xmlResponse(xmlResponse)
            }

            override fun setServerState(serverState: Map<String, Any?>) {}
        })
    }

    @Test
    @Throws(Throwable::class)
    fun serverSetupDoneUsingFixtureDataWithoutTables() {
        val contractGherkin = "Feature: Contract for /locations API\n" +
                "  Scenario: \n" +
                "    * fixture cities {\"cities\": [{\"city\": \"Mumbai\"}, {\"city\": \"Bangalore\"}]}\n" +
                "    * pattern City {\"city\": \"(string)\"}\n" +
                "    * pattern Cities {\"cities\": [\"(City*)\"]}\n" +
                "    Given fact cities\n" +
                "    When GET /locations\n" +
                "    Then status 200\n" +
                "    And response-body (Cities)"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val setupStatus = arrayOf("Setup didn\"t happen")
        val setupSuccess = "Setup happened"
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/locations", request.path)
                assertEquals("GET", request.method)
                val jsonResponse = "{\"cities\": [{\"city\": \"Mumbai\"}, {\"city\": \"Bangalore\"}]}"
                return jsonResponse(jsonResponse)
            }

            override fun setServerState(serverState: Map<String, Any?>) {
                setupStatus[0] = setupSuccess
                assertTrue(serverState.containsKey("cities"))
                assertTrue(serverState["cities"] !is Boolean)
            }
        })
        assertEquals("Setup happened", setupStatus[0])
    }

    @Test
    @Throws(Throwable::class)
    fun serverSetupDoneUsingFixtureDataWithTables() {
        val contractGherkin = """Feature: Contract for /locations API
  Scenario Outline: 
    * fixture city_list {"cities": [{"city": "Mumbai"}, {"city": "Bangalore"}]}
    * pattern City {"city": "(string)"}
    * pattern Cities {"cities": ["(City*)"]}
    Given fact cities_exist 
    When GET /locations
    Then status 200
    And response-body (Cities)
  Examples:
  | cities_exist | 
  | city_list | 
    """
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val setupStatus = arrayOf("Setup didn\"t happen")
        val setupSuccess = "Setup happened"
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/locations", request.path)
                assertEquals("GET", request.method)
                val jsonResponse = "{\"cities\": [{\"city\": \"Mumbai\"}, {\"city\": \"Bangalore\"}]}"
                return jsonResponse(jsonResponse)
            }

            override fun setServerState(serverState: Map<String, Any?>) {
                setupStatus[0] = setupSuccess
                assertTrue(serverState.containsKey("cities_exist"))
                assertTrue(serverState["cities_exist"] !is Boolean)
            }
        })
        assertEquals("Setup happened", setupStatus[0])
    }

    @Test
    @Throws(Throwable::class)
    fun randomFactNumberIsPickedUpByGeneratedPattern() {
        val contractGherkin = "Feature: Pet API\n\n" +
                "Scenario: Update pet details\n" +
                "  Given pattern Pet {\"name\": \"(string)\", \"type\": \"(string)\", \"status\": \"(string)\", \"id\": \"(number)\"}\n" +
                "  And fact id (number)\n" +
                "  When POST /pets\n" +
                "  And request-body (Pet)\n" +
                "  Then status 200\n"
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val setupStatus = arrayOf("Setup didn\"t happen")
        val setupSuccess = "Setup happened"
        val idFound = intArrayOf(0)
        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/pets", request.path)
                assertEquals("POST", request.method)
                val requestBody = JSONObject(request.bodyString)
                assertEquals(idFound[0], requestBody.getInt("id"))
                return HttpResponse.EMPTY_200
            }

            override fun setServerState(serverState: Map<String, Any?>) {
                setupStatus[0] = setupSuccess
                assertTrue(serverState.containsKey("id"))
                idFound[0] = serverState["id"].toString().toInt()
            }
        })
        assertEquals("Setup happened", setupStatus[0])
    }

    @Test
    @Throws(Throwable::class)
    fun `random fact number is picked up by the tabular pattern`() {
        val contractGherkin = """
Feature: Pet API

Scenario: Update pet details
  Given pattern Pet
  | name   | (string) |
  | type   | (string) |
  | status | (string) |
  | id     | (number) |
  And fact id (number)
  When POST /pets
  And request-body (Pet)
  Then status 200
"""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val setupStatus = arrayOf("Setup didn't happen")
        val setupSuccess = "Setup happened"
        val idFound = intArrayOf(0)

        contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertEquals("/pets", request.path)
                assertEquals("POST", request.method)
                val requestBody = JSONObject(request.bodyString)
                assertEquals(idFound[0], requestBody.getInt("id"))
                return HttpResponse.EMPTY_200
            }

            override fun setServerState(serverState: Map<String, Any?>) {
                setupStatus[0] = setupSuccess
                assertTrue(serverState.containsKey("id"))
                idFound[0] = serverState["id"].toString().toInt()
            }
        })
        assertEquals("Setup happened", setupStatus[0])
    }

    @Test
    fun `A number is generated in the request body and matched in the response`() {
        val contractGherkin = """
Feature: Number API

Scenario: GET and POST number
  When POST /number
  And request-body (number)
  Then status 200
  And response-body (number)
"""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val flags = mutableMapOf<String, Boolean>()

        val executionInfo = contractBehaviour.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                flags["executed"] = true
                assertEquals("/number", request.path)
                assertEquals("POST", request.method)
                assertTrue(request.body is NumberValue)
                return HttpResponse(200, "10")
            }

            override fun setServerState(serverState: Map<String, Any?>) {
            }
        })

        assertTrue(flags["executed"] ?: false)
        if(executionInfo.unsuccessfulInteractionCount() > 0)
            executionInfo.print()
        assertEquals(0, executionInfo.unsuccessfulInteractionCount())
    }

    @Test
    fun `when a contract is run as test against another contract fake, it should fail if the two contracts are incompatible`() {
        val oldContract = """Feature: Contract
Scenario:
Given GET /
Then status 200
"""

        val newContract = """Feature: Contract
Scenario:
Given GET /d
Then status 200"""

        assertThrows(Throwable::class.java) {
            Contract.fromGherkin(newContract, 0, 0).test(ContractFake(oldContract, "localhost", 9000))
        }
    }
}