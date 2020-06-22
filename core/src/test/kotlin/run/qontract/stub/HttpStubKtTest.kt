package run.qontract.stub

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Feature
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.mock.ScenarioStub

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
        assertThat(stubResponse.headers).containsEntry("X-Qontract-Result", "failure")
        assertThat(stubResponse.body?.toStringValue()).isEqualTo("""STRICT MODE ON

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
        assertThat(stubResponse.headers).containsEntry("X-Qontract-Result", "failure")
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
        assertThat(endPointFromHostAndPort("localhost", null)).isEqualTo("http://localhost")
    }

    @Test
    fun `generates a valid endpoint when a non 80 port is given`() {
        assertThat(endPointFromHostAndPort("localhost", 9000)).isEqualTo("http://localhost:9000")
    }

    @Test
    fun `generates a valid endpoint when port 80 is given`() {
        assertThat(endPointFromHostAndPort("localhost", 80)).isEqualTo("http://localhost")
    }
}