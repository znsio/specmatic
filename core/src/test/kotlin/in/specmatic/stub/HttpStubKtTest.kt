package `in`.specmatic.stub

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import `in`.specmatic.core.log.consoleLog
import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.*
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.test.HttpClient
import java.security.KeyStore
import java.util.*
import `in`.specmatic.stubResponse

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

        val feature = parseGherkinStringToFeature(gherkin)

        val request = HttpRequest(method = "POST", path = "/", body = NumberValue(10))
        val scenarioStub = ScenarioStub(request, HttpResponse.OK, null)

        val stubData = feature.matchingStub(scenarioStub)
        val stubResponse = stubResponse(HttpRequest(method = "POST", path = "/", body = StringValue("Hello")), listOf(feature), listOf(stubData), true)

        assertResponseFailure(stubResponse, """STRICT MODE ON

>> REQUEST.BODY
  
  Expected number, actual was "Hello"""")
    }

    private fun assertResponseFailure(stubResponse: HttpStubResponse, errorMessage: String) {
        assertThat(stubResponse.response.status).isEqualTo(400)
        assertThat(stubResponse.response.headers).containsEntry(SPECMATIC_RESULT_HEADER, "failure")
        assertThat(stubResponse.response.body.toStringLiteral()).isEqualTo(errorMessage)
    }

    @Test
    fun `undeclared keys should not be accepted by stub without expectations`() {
        val request = HttpRequest(method = "POST", path = "/", body = parsedValue("""{"number": 10, "undeclared": 20}"""))

        val feature = parseGherkinStringToFeature("""
Feature: POST API
  Scenario: Test
    When POST /
    And request-body
      | number | (number) |
    Then status 200
""".trim())

        val stubResponse = stubResponse(request, listOf(feature), emptyList(), false)

        assertResponseFailure(stubResponse,
            """
            In scenario "Test"
            API: POST / -> 200
            
              >> REQUEST.BODY
              
              Key named "undeclared" was unexpected
            """.trimIndent())
    }

    @Test
    fun `unexpected headers should be accepted by a stub without expectations`() {
        val request = HttpRequest(method = "GET", path = "/", headers = mapOf("X-Expected" to "data", "Accept" to "text"))

        val feature = parseGherkinStringToFeature("""
Feature: GET API
  Scenario: Test
    When GET /
    And request-header X-Expected (string)
    Then status 200
""".trim())

        val stubResponse = stubResponse(request, listOf(feature), emptyList(), false)

        assertThat(stubResponse.response.status).isEqualTo(200)
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
        assertThat(endPointFromHostAndPort("localhost", 80, KeyData(KeyStore.getInstance(KeyStore.getDefaultType()), ""))).isEqualTo("https://localhost")
    }

    @Test
    fun `should not match extra keys in the request`() {
        val feature = parseGherkinStringToFeature("""
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

        assertResponseFailure(response,
            """
            In scenario "Square of a number"
            API: POST /number -> 200
            
              >> REQUEST.BODY
              
              Key named "unexpected" was unexpected
            """.trimIndent())
    }

    @Test
    fun `stub should not match a request in which all the query params are missing`() {
        val feature = parseGherkinStringToFeature("""
Feature: Math API

Scenario: Square of a number
  When GET /count?status=(string)
  Then status 200
  And response-body (string)
""".trim())

        val stubRequest = HttpRequest(method = "GET", path = "/count", queryParams = mapOf("status" to "available"))
        val stubResponse = HttpResponse.OK("data")
        val stubData = HttpStubData(
            stubRequest.toPattern(),
            stubResponse,
            Resolver(),
            responsePattern = HttpResponsePattern()
        )

        val request = HttpRequest(method = "GET", path = "/count")
        val response = stubResponse(request, listOf(feature), listOf(stubData), false)
        assertThat(response.response.status).isEqualTo(200)

        val strictResponse = stubResponse(request, listOf(feature), listOf(stubData), true)
        assertResponseFailure(strictResponse, """STRICT MODE ON

>> REQUEST.URL.QUERY-PARAMS
  
  Expected query param named "status" was missing""")
    }

    @Test
    fun `should not generate any key from the ellipsis in the response`() {
        val feature = parseGherkinStringToFeature("""
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

        assertThat(response.response.status).isEqualTo(200)
        val bodyValue = response.response.body as JSONObjectValue
        assertThat(bodyValue.jsonObject).hasSize(1)
        assertThat(bodyValue.jsonObject.getValue("number")).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `invalid stub request`() {
        val feature = parseGherkinStringToFeature("""
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
        val response = stubResponse(actualRequest, listOf(feature), listOf(HttpStubData(
            stubSetupRequest.toPattern(),
            HttpResponse(status = 200, body = parsedJSON("""{"10": 10}""")),
            feature.scenarios.single().resolver,
            responsePattern = HttpResponsePattern()
        )), false)
        assertThat(response.response.status).isEqualTo(400)
    }

    @Test
    fun `stub should validate expectations and serve generated xml when the namespace prefix changes`() {
        val feature = parseGherkinStringToFeature("""
Feature: XML namespace prefix
  Scenario: Request has namespace prefixes
    When POST /
    And request-body <ns1:customer xmlns:ns1="http://example.com/customer"><name>(string)</name></ns1:customer>
    Then status 200
        """.trimIndent())

        val stubSetupRequest = HttpRequest().updateMethod("POST").updatePath("/")
        val actualRequest = HttpRequest(method = "POST", path = "/", body = toXMLNode("""<ns2:customer xmlns:ns2="http://example.com/customer"><name>John Doe</name></ns2:customer>"""))
        val response = stubResponse(actualRequest, listOf(feature), listOf(HttpStubData(
            stubSetupRequest.toPattern(),
            HttpResponse.OK,
            feature.scenarios.single().resolver,
            responsePattern = HttpResponsePattern()
        )), false)
        assertThat(response.response.status).isEqualTo(200)
    }

    @Test
    fun `multithreading test`() {
        val feature = parseGherkinStringToFeature("""
Feature: POST API
  Scenario: Test
    Given type Number
      | number | (number) |
    When POST /
    And form-field Data (Number)
    Then status 200
    And response-body (Number)
""".trim())

        val errors: Vector<String> = Vector()

        HttpStub(feature).use { stub ->
            usingMultipleThreads(30) { stubNumber ->
                `set an expectation and exercise it`(stubNumber, stub)?.let {
                    errors.add(it)
                }
            }
        }

        if(errors.isNotEmpty()) {
            val errorMessages = errors.joinToString("\n\n")
            fail("Got errors\n$errorMessages")
        }
    }

    private fun usingMultipleThreads(threadCount: Int, fn: (Int) -> Unit) {
        val range = 0 until threadCount
        val threads = range.map { stubNumber ->
            Thread {
                val base = stubNumber * 10

                (0..2).forEach { increment ->
                    fn(base + increment)
                }
            }
        }

        start(threads)
        waitFor(threads)
    }

    private fun `set an expectation and exercise it`(stubNumber: Int, stub: HttpStub): String? {
        return try {
            val error = createExpectation(stubNumber, stub)
            if (error != null)
                return "Creating expectation $stubNumber:\n $error"

            val response = invokeStub(stubNumber, stub)
            println(response.body.toStringLiteral())

            val json = try {
                response.body as JSONObjectValue
            } catch (e: Throwable) {
                return "Got the following bad response:\n${response.toLogString()}"
            }

            val numberInResponse = try {

                json.jsonObject.getValue("number").toStringLiteral().toInt()
            } catch (e: Throwable) {
                return exceptionCauseMessage(e)
            }

            when (numberInResponse) {
                stubNumber -> null
                else -> "Expected response to contain $stubNumber, but instead got $numberInResponse"
            }
        } catch (e: Throwable) {
            exceptionCauseMessage(e)
        }
    }

    @Test
    fun `soft casting xml value to xml`() {
        val value = softCastValueToXML(StringValue("<xml>data</xml"))
        assertThat(value).isInstanceOf(XMLValue::class.java)
        assertThat(value.toStringLiteral()).isEqualTo("<xml>data</xml")
    }

    @Test
    fun `soft casting non xml value to xml`() {
        val value = softCastValueToXML(StringValue("not xml"))
        assertThat(value).isInstanceOf(StringValue::class.java)
        assertThat(value.toStringLiteral()).isEqualTo("not xml")
    }

    @Test
    fun `kafka stubs should not be picked up when creating http expectations`() {
        val feature = parseGherkinStringToFeature("""
            Feature: Kafka
              Scenario: Kafka 
                * kafka-message customer data
        """.trimIndent())
        val kafkaStubs = contractInfoToHttpExpectations(listOf(feature to emptyList()))
        assertThat(kafkaStubs.isEmpty())
    }

    @Test
    fun `routing functions`() {
        assertThat(isFetchLogRequest(HttpRequest("GET", "/_$APPLICATION_NAME_LOWER_CASE/log"))).isTrue()
        assertThat(isFetchLoadLogRequest(HttpRequest("GET", "/_$APPLICATION_NAME_LOWER_CASE/load_log"))).isTrue()
        assertThat(isFetchContractsRequest(HttpRequest("GET", "/_$APPLICATION_NAME_LOWER_CASE/contracts"))).isTrue()
        assertThat(isExpectationCreation(HttpRequest("POST", "/_$APPLICATION_NAME_LOWER_CASE/expectations"))).isTrue()
        assertThat(isStateSetupRequest(HttpRequest("POST", "/_$APPLICATION_NAME_LOWER_CASE/state"))).isTrue()
    }

    private fun createStubsUsingMultipleThreads(range: IntRange, stub: HttpStub) {
        val threads = range.map { i ->
            println("STARTING THREAD $i")
            Thread { createExpectation(i, stub) }
        }

        start(threads)
        waitFor(threads)
    }

    private fun waitFor(threads: List<Thread>) = threads.forEach { it.join() }

    private fun start(threads: List<Thread>) = threads.forEach { it.start() }

    private fun invokeStub(i: Int, stub: HttpStub): HttpResponse {
        val request = HttpRequest(method = "POST", path = "/", formFields = mapOf("Data" to """{"number": $i}"""))
        val client = HttpClient(stub.endPoint)
        return client.execute(request)
    }

    private fun createExpectation(stubNumber: Int, stub: HttpStub): String? {
        val stubInfo = """
    {
      "http-request": {
        "method": "POST",
        "path": "/",
        "form-fields": {
          "Data": "{\"number\": $stubNumber}"
        }
      },
      "http-response": {
        "status": 200,
        "body": {"number": $stubNumber}
      }
    }
    """.trimIndent()

        val client = HttpClient(stub.endPoint, log = ::consoleLog)
        val request = HttpRequest("POST", path = "/_specmatic/expectations", body = parsedValue(stubInfo))
        val response = client.execute(request)

        return when(response.status) {
            200 -> null
            else -> {
                "Response for stub number $stubNumber:\n${response.toLogString()}"
            }
        }
    }
}