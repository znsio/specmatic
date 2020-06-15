package run.qontract.stub

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Feature
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.mock.ScenarioStub

internal class HttpStubKtTest {
    @Test
    fun temp() {
//        fun stubResponse(httpRequest: HttpRequest, behaviours: List<Feature>, stubs: List<HttpStubData>, strictMode: Boolean): HttpResponse {

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
        assertThat(stubResponse.body?.toStringValue()).isEqualTo(""">> REQUEST.BODY

Expected number, actual was "Hello"""")
    }
}