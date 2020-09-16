package run.qontract.stub

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.consoleLog
import run.qontract.core.*
import run.qontract.core.pattern.parsedJSON
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.XMLValue
import run.qontract.mock.ScenarioStub
import run.qontract.test.HttpClient
import java.security.KeyStore

internal class HttpStubKtTest {
    @Test
    fun `in strict mode the stub replies with an explanation of what broke instead of a randomly generated response`() {
        val gherkin = """
Feature: Test
  Scenario: Test
    When POST /
    And request-body (number)
    Then status 200
""".trim()

        val feature = Feature(gherkin)

        val request = HttpRequest(method = "POST", path = "/", body = NumberValue(10))
        val scenarioStub = ScenarioStub(request, HttpResponse.OK, null)

        val stubData = feature.matchingStub(scenarioStub)
        val stubResponse = stubResponse(HttpRequest(method = "POST", path = "/", body = StringValue("Hello")), listOf(feature), listOf(stubData), true)

        assertThat(stubResponse.status).isEqualTo(400)
        assertThat(stubResponse.headers).containsEntry(QONTRACT_RESULT_HEADER, "failure")
        assertThat(stubResponse.body.toStringValue()).isEqualTo("""STRICT MODE ON

>> REQUEST.BODY

Expected number, actual was "Hello"""")
    }

    @Test
    fun `undeclared keys should not be accepted by stub without expectations`() {
        val request = HttpRequest(method = "POST", path = "/", body = parsedValue("""{"number": 10, "undeclared": 20}"""))

        val feature = Feature("""
Feature: POST API
  Scenario: Test
    When POST /
    And request-body
      | number | (number) |
    Then status 200
""".trim())

        val stubResponse = stubResponse(request, listOf(feature), emptyList(), false)

        assertThat(stubResponse.status).isEqualTo(400)
        assertThat(stubResponse.headers).containsEntry(QONTRACT_RESULT_HEADER, "failure")
        assertThat(stubResponse.body).isEqualTo(StringValue("""In scenario "Test"
>> REQUEST.BODY

Key undeclared was unexpected"""))
    }

    @Test
    fun `unexpected headers should be accepted by a stub without expectations`() {
        val request = HttpRequest(method = "GET", path = "/", headers = mapOf("X-Expected" to "data", "Accept" to "text"))

        val feature = Feature("""
Feature: GET API
  Scenario: Test
    When GET /
    And request-header X-Expected (string)
    Then status 200
""".trim())

        val stubResponse = stubResponse(request, listOf(feature), emptyList(), false)

        assertThat(stubResponse.status).isEqualTo(200)
    }

    @Test
    fun `generates a valid endpoint when a port is not given`() {
        assertThat(endPointFromHostAndPort("localhost", null, null)).isEqualTo("http://localhost")
    }

    @Test
    fun `generates a valid endpoint when a non 80 port is given`() {
        assertThat(endPointFromHostAndPort("localhost", 9000, null)).isEqualTo("http://localhost:9000")
    }

    @Test
    fun `generates a valid endpoint when port 80 is given`() {
        assertThat(endPointFromHostAndPort("localhost", 80, null)).isEqualTo("http://localhost")
    }

    @Test
    fun `generates an https endpoint when key store data is provided`() {
        assertThat(endPointFromHostAndPort("localhost", 80, KeyStoreData(KeyStore.getInstance(KeyStore.getDefaultType()), ""))).isEqualTo("https://localhost")
    }

    @Test
    fun `should not match extra keys in the request`() {
        val feature = Feature("""
Feature: Math API

Scenario: Square of a number
  When POST /number
  And request-body
  | number | (number) |
  | ...    |          |
  Then status 200
""".trim())
        
        val request = HttpRequest(method = "POST", path = "/number", body = parsedValue("""{"number": 10, "unexpected": "data"}"""))
        val response = stubResponse(request, listOf(feature), emptyList(), false)

        assertThat(response.body).isEqualTo(StringValue("""In scenario "Square of a number"
>> REQUEST.BODY

Key unexpected was unexpected"""))
    }

    @Test
    fun `stub should not match a request in which all the query params are missing`() {
        val feature = Feature("""
Feature: Math API

Scenario: Square of a number
  When GET /count?status=(string)
  Then status 200
  And response-body (string)
""".trim())

        val stubRequest = HttpRequest(method = "GET", path = "/count", queryParams = mapOf("status" to "available"))
        val stubResponse = HttpResponse.OK("data")
        val stubData = HttpStubData(stubRequest.toPattern(), stubResponse, Resolver())

        val request = HttpRequest(method = "GET", path = "/count")
        val response = stubResponse(request, listOf(feature), listOf(stubData), false)
        assertThat(response.status).isEqualTo(200)

        val strictResponse = stubResponse(request, listOf(feature), listOf(stubData), true)
        assertThat(strictResponse.status).isEqualTo(400)
        assertThat(strictResponse.body.toStringValue().trim()).isEqualTo("""STRICT MODE ON

>> REQUEST.URL.QUERY-PARAMS

Expected query param status was missing""")
    }

    @Test
    fun `should not generate any key from the ellipsis in the response`() {
        val feature = Feature("""
Feature: Math API

Scenario: Square of a number
  When GET /number
  Then status 200
  And response-body
  | number | (number) |
  | ...    |          |
""".trim())

        val request = HttpRequest(method = "GET", path = "/number")
        val response = stubResponse(request, listOf(feature), emptyList(), false)

        assertThat(response.status).isEqualTo(200)
        val bodyValue = response.body as JSONObjectValue
        assertThat(bodyValue.jsonObject).hasSize(1)
        assertThat(bodyValue.jsonObject.getValue("number")).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `invalid stub request`() {
        val feature = Feature("""
Feature: Math API

Scenario: Square of a number
  Given json Information
    | id   | (number) |
    | data | (More)   |
  And json More
    | info | (number) |
  When POST /number
  And form-field Data (
  Then status 200
  And response-body
    | number | (number) |
""".trim())

        val stubSetupRequest = HttpRequest(method = "GET", path = "/number", formFields = mapOf("Data" to """{"id": 10, "data": {"info": 20} }"""))
        val actualRequest = HttpRequest(method = "GET", path = "/number", formFields = mapOf("NotData" to """{"id": 10, "data": {"info": 20} }"""))
        val response = stubResponse(actualRequest, listOf(feature), listOf(HttpStubData(stubSetupRequest.toPattern(), HttpResponse(status = 200, body = parsedJSON("""{"10": 10}""")), feature.scenarios.single().resolver)), false)
        assertThat(response.status).isEqualTo(400)
    }

    @Test
    fun `multithreading test`() {
        val feature = Feature("""
Feature: POST API
  Scenario: Test
    Given type Number
      | number | (number) |
    When POST /
    And form-field Data (Number)
    Then status 200
    And response-body (Number)
""".trim())

        val range = 0..9

        HttpStub(feature).use { stub ->
            createStubsUsingMultipleThreads(range, stub)

            range.forEach { stubNumber ->
                val response = invokeStub(stubNumber, stub)
                println(response.body.toStringValue())
                val json = response.body as JSONObjectValue
                val numberInResponse = json.jsonObject.getValue("number").toStringValue().toInt()

                assertThat(numberInResponse).isEqualTo(stubNumber)
            }
        }
    }

    @Test
    fun `soft casting xml value to xml`() {
        val value = softCastValueToXML(StringValue("<xml>data</xml"))
        assertThat(value).isInstanceOf(XMLValue::class.java)
        assertThat(value.toStringValue()).isEqualTo("<xml>data</xml")
    }

    @Test
    fun `soft casting non xml value to xml`() {
        val value = softCastValueToXML(StringValue("not xml"))
        assertThat(value).isInstanceOf(StringValue::class.java)
        assertThat(value.toStringValue()).isEqualTo("not xml")
    }

    @Test
    fun `kafka stubs should not be picked up when creating http expectations`() {
        val feature = Feature("""
            Feature: Kafka
              Scenario: Kafka 
                * kafka-message customer data
        """.trimIndent())
        val kafkaStubs = contractInfoToHttpExpectations(listOf(feature to emptyList()))
        assertThat(kafkaStubs.isEmpty())
    }

    @Test
    fun `routing functions`() {
        assertThat(isFetchLogRequest(HttpRequest("GET", "/_qontract/log"))).isTrue()
        assertThat(isFetchLoadLogRequest(HttpRequest("GET", "/_qontract/load_log"))).isTrue()
        assertThat(isFetchContractsRequest(HttpRequest("GET", "/_qontract/contracts"))).isTrue()
        assertThat(isExpectationCreation(HttpRequest("POST", "/_qontract/expectations"))).isTrue()
        assertThat(isStateSetupRequest(HttpRequest("POST", "/_qontract/state"))).isTrue()
    }

    private fun createStubsUsingMultipleThreads(range: IntRange, stub: HttpStub) {
        val threads = range.map { i ->
            println("STARTING THREAD $i")
            val thread = Thread { createExpectation(i, stub) }
            thread
        }

        start(threads)
        waitFor(threads)
    }

    private fun waitFor(threads: List<Thread>) {
        for(thread in threads) {
            thread.join()
        }
    }

    private fun start(threads: List<Thread>) {
        for(thread in threads) {
            thread.start()
        }
    }

    private fun invokeStub(i: Int, stub: HttpStub): HttpResponse {
        val request = HttpRequest(method = "POST", path = "/", formFields = mapOf("Data" to """{"number": $i}"""))
        val client = HttpClient(stub.endPoint)
        return client.execute(request)
    }

    private fun createExpectation(i: Int, stub: HttpStub) {
        val stubInfo = """
    {
      "http-request": {
        "method": "POST",
        "path": "/",
        "form-fields": {
          "Data": "{\"number\": $i}"
        }
      },
      "http-response": {
        "status": 200,
        "body": {"number": $i}
      }
    }
    """.trimIndent()

        val client = HttpClient(stub.endPoint, log = ::consoleLog)
        val request = HttpRequest("POST", path = "/_qontract/expectations", body = parsedValue(stubInfo))
        val response = client.execute(request)
        assertThat(response.status).isEqualTo(200)
    }
}