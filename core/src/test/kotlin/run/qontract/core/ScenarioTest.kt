package run.qontract.core

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.*
import run.qontract.core.value.*
import run.qontract.mock.ScenarioStub
import java.util.*
import kotlin.collections.HashMap

internal class ScenarioTest {
    @Test
    fun `should generate one test scenario when there are no examples`() {
        val scenario = Scenario(
            "test",
            HttpRequestPattern(),
            HttpResponsePattern(),
            HashMap(),
            LinkedList(),
            HashMap(),
            HashMap(),
            KafkaMessagePattern(),
        )
        scenario.generateTestScenarios().let {
            assertThat(it.size).isEqualTo(1)
        }
    }

    @Test
    fun `should generate two test scenarios when there are two rows in examples`() {
        val patterns = Examples(emptyList(), listOf(Row(), Row()))
        val scenario = Scenario(
            "test",
            HttpRequestPattern(),
            HttpResponsePattern(),
            HashMap(),
            listOf(patterns),
            HashMap(),
            HashMap(),
            KafkaMessagePattern(),
        )
        scenario.generateTestScenarios().let {
            assertThat(it.size).isEqualTo(2)
        }
    }

    @Test
    fun `should not match when there is an Exception`() {
        val httpResponsePattern = mockk<HttpResponsePattern>(relaxed = true)
        every { httpResponsePattern.matches(any(), any()) }.throws(ContractException("message"))
        val scenario = Scenario(
            "test",
            HttpRequestPattern(),
            httpResponsePattern,
            HashMap(),
            LinkedList(),
            HashMap(),
            HashMap(),
            KafkaMessagePattern(),
        )
        scenario.matches(HttpResponse.EMPTY).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf(), listOf("Exception: message")))
        }
    }

    @Test
    fun `given a pattern in an example, facts declare without a value should pick up the pattern`() {
        val row = Row(listOf("id"), listOf("(string)"))

        val newState = newExpectedServerStateBasedOn(row, mapOf("id" to True), HashMap(), Resolver())

        assertThat(newState.getValue("id").toStringValue()).isNotEqualTo("(string)")
        assertThat(newState.getValue("id").toStringValue().trim().length).isGreaterThan(0)
    }

    @Test
    fun `given a pattern in an example in a scenario generated based on a row, facts declare without a value should pick up the pattern`() {
        val row = Row(listOf("id"), listOf("(string)"))
        val example = Examples(listOf("id"), listOf(row))

        val state = HashMap(mapOf<String, Value>("id" to True))
        val scenario = Scenario(
            "Test",
            HttpRequestPattern(urlMatcher = URLMatcher(emptyMap(), emptyList(), path="/")),
            HttpResponsePattern(status=200),
            state,
            listOf(example),
            HashMap(),
            HashMap(),
            KafkaMessagePattern(),
        )

        val testScenarios = scenario.generateTestScenarios()
        val newState = testScenarios.first().expectedFacts

        assertThat(newState.getValue("id").toStringValue()).isNotEqualTo("(string)")
        assertThat(newState.getValue("id").toStringValue().trim().length).isGreaterThan(0)
    }

    @Test
    fun `scenario will match a kafka mock message`() {
        val kafkaMessagePattern = KafkaMessagePattern("customers", StringPattern, StringPattern)
        val scenario = Scenario(
            "Test",
            HttpRequestPattern(),
            HttpResponsePattern(),
            emptyMap(),
            emptyList(),
            emptyMap(),
            emptyMap(),
            kafkaMessagePattern,
        )

        val kafkaMessage = KafkaMessage("customers", StringValue("name"), StringValue("John Doe"))
        assertThat(scenario.matchesMock(kafkaMessage)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `will not match a mock http request with unexpected request headers`() {
        val scenario = Scenario(
            "Test",
            HttpRequestPattern(method="GET", urlMatcher = URLMatcher(emptyMap(), emptyList(), "/"), headersPattern = HttpHeadersPattern(mapOf("X-Expected" to StringPattern))),
            HttpResponsePattern(status = 200),
            emptyMap(),
            emptyList(),
            emptyMap(),
            emptyMap(),
            null,
        )
        val mockRequest = HttpRequest(method = "GET", path = "/", headers = mapOf("X-Expected" to "value", "X-Unexpected" to "value"))
        val mockResponse = HttpResponse.OK

        assertThat(scenario.matchesMock(mockRequest, mockResponse)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `will not match a mock http request with unexpected response headers`() {
        val scenario = Scenario(
            "Test",
            HttpRequestPattern(method="GET", urlMatcher = URLMatcher(emptyMap(), emptyList(), "/"), headersPattern = HttpHeadersPattern(emptyMap())),
            HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Expected" to StringPattern))),
            emptyMap(),
            emptyList(),
            emptyMap(),
            emptyMap(),
            null,
        )
        val mockRequest = HttpRequest(method = "GET", path = "/")
        val mockResponse = HttpResponse.OK.copy(headers = mapOf("X-Expected" to "value", "X-Unexpected" to "value"))

        assertThat(scenario.matchesMock(mockRequest, mockResponse)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `will not match a mock http request with unexpected query params`() {
        val scenario = Scenario(
            "Test",
            HttpRequestPattern(method="GET", urlMatcher = URLMatcher(mapOf("expected" to StringPattern), emptyList(), "/"), headersPattern = HttpHeadersPattern(emptyMap(), null)),
            HttpResponsePattern(status = 200),
            emptyMap(),
            emptyList(),
            emptyMap(),
            emptyMap(),
            null,
        )
        val mockRequest = HttpRequest(method = "GET", path = "/", queryParams = mapOf("expected" to "value", "unexpected" to "value"))
        val mockResponse = HttpResponse.OK

        assertThat(scenario.matchesMock(mockRequest, mockResponse)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `will not match a mock json body with unexpected keys`() {
        val scenario = Scenario(
            "Test",
            HttpRequestPattern(method="POST", urlMatcher = URLMatcher(mapOf("expected" to StringPattern), emptyList(), "/"), headersPattern = HttpHeadersPattern(emptyMap(), null), body = parsedPattern("""{"expected": "value"}""")),
            HttpResponsePattern(status = 200),
            emptyMap(),
            emptyList(),
            emptyMap(),
            emptyMap(),
            null,
        )
        val mockRequest = HttpRequest(method = "POST", path = "/", body = parsedValue("""{"unexpected": "value"}"""))
        val mockResponse = HttpResponse.OK

        assertThat(scenario.matchesMock(mockRequest, mockResponse)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should mock a header with a pattern value`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When GET /resource
And request-header X-RequestKey (number)
Then status 200
And response-header X-ResponseKey (number)
        """.trim()

        val request = HttpRequest("GET", "/resource", mapOf("X-RequestKey" to "(number)"), EmptyString)
        val response = HttpResponse(200, "", mapOf("X-ResponseKey" to "(number)"))
        val stub = ScenarioStub(request, response)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("GET", "/resource", mapOf("X-RequestKey" to "10")), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertDoesNotThrow { matchingResponse.response.headers.getValue("X-ResponseKey").toInt() }
    }

    @Test
    fun `should mock a header with a primitive number`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When GET /resource
And request-header X-RequestKey (number)
Then status 200
And response-header X-ResponseKey (number)
        """.trim()

        val request = HttpRequest("GET", "/resource", mapOf("X-RequestKey" to "10"), EmptyString)
        val response = HttpResponse(200, "", mapOf("X-ResponseKey" to "20"))
        val stub = ScenarioStub(request, response)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("GET", "/resource", mapOf("X-RequestKey" to "10")), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertThat(matchingResponse.response.headers.getValue("X-ResponseKey")).isEqualTo("20")
    }

    @Test
    fun `should mock a query with a number type`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When GET /resource?query=(number)
Then status 200
        """.trim()

        val request = HttpRequest("GET", "/resource", queryParams = mapOf("query" to "(number)"))
        val stub = ScenarioStub(request, HttpResponse.OK)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("GET", "/resource", queryParams = mapOf("query" to "10")), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertThat(matchingResponse.response.status).isEqualTo(200)
    }

    @Test
    fun `should mock a query with a primitive number`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When GET /resource?query=(number)
Then status 200
        """.trim()

        val request = HttpRequest("GET", "/resource", queryParams = mapOf("query" to "10"))
        val stub = ScenarioStub(request, HttpResponse.OK)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("GET", "/resource", queryParams = mapOf("query" to "10")), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertThat(matchingResponse.response.status).isEqualTo(200)
    }

    @Test
    fun `should mock a query with a boolean type`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When GET /resource?query=(boolean)
Then status 200
        """.trim()

        val request = HttpRequest("GET", "/resource", queryParams = mapOf("query" to "(boolean)"))
        val stub = ScenarioStub(request, HttpResponse.OK)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("GET", "/resource", queryParams = mapOf("query" to "true")), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertThat(matchingResponse.response.status).isEqualTo(200)
    }

    @Test
    fun `should mock a query with a primitive boolean`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When GET /resource?query=(boolean)
Then status 200
        """.trim()

        val request = HttpRequest("GET", "/resource", queryParams = mapOf("query" to "true"))
        val stub = ScenarioStub(request, HttpResponse.OK)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("GET", "/resource", queryParams = mapOf("query" to "true")), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertThat(matchingResponse.response.status).isEqualTo(200)
    }

    @Test
    fun `should mock a form field with a pattern value`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When POST /resource
And form-field value (number)
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/resource", formFields = mapOf("value" to "(number)"))
        val stub = ScenarioStub(request, HttpResponse.OK)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("POST", "/resource", formFields = mapOf("value" to "10")), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertThat(matchingResponse.response.status).isEqualTo(200)
    }

    @Test
    fun `should mock a form field with a primitive value`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When POST /resource
And form-field value (number)
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/resource", formFields = mapOf("value" to "10"))
        val stub = ScenarioStub(request, HttpResponse.OK)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("POST", "/resource", formFields = mapOf("value" to "10")), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertThat(matchingResponse.response.status).isEqualTo(200)
    }

    @Test
    fun `should mock a multipart part with a pattern value`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When POST /resource
And request-part value (number)
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/resource", multiPartFormData = listOf(MultiPartContentValue("value", StringValue("(number)"))))
        val stub = ScenarioStub(request, HttpResponse.OK)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("POST", "/resource", multiPartFormData = listOf(MultiPartContentValue("value", StringValue("10")))), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertThat(matchingResponse.response.status).isEqualTo(200)
    }

    @Test
    fun `should mock a multipart part with a primitive value`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When POST /resource
And request-part value (number)
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/resource", multiPartFormData = listOf(MultiPartContentValue("value", StringValue("10"))))
        val stub = ScenarioStub(request, HttpResponse.OK)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("POST", "/resource", multiPartFormData = listOf(MultiPartContentValue("value", StringValue("10")))), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertThat(matchingResponse.response.status).isEqualTo(200)
    }

    @Test
    fun `should mock a body with a primitive pattern`() {
        val gherkin = """Feature: Test API
Scenario: Test Scenario
When POST /resource
And request-body (number)
Then status 200
And response-body (number)
        """.trim()

        val request = HttpRequest("POST", "/resource", body = StringValue("(number)"))
        val response = HttpResponse(200, body = StringValue("(number)"))
        val stub = ScenarioStub(request, response)

        val feature = Feature(gherkin)

        val requestPattern = request.toPattern()
        assertThat(requestPattern.matches(HttpRequest("POST", "/resource", body = StringValue("10")), Resolver())).isInstanceOf(Result.Success::class.java)

        val matchingResponse = feature.matchingStub(stub)
        assertDoesNotThrow { matchingResponse.response.body.toStringValue().toInt() }
    }
}
