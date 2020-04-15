package run.qontract.fake

import org.junit.jupiter.api.Assertions.*
import org.assertj.core.api.Assertions.*

import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.value.NumberValue
import run.qontract.mock.MockScenario

internal class ContractFakeTest {
    @Test
    fun `should serve mocked data before stub`() {
        val gherkin = """
Feature: Math API

Scenario: Square of a number
  When POST /number
  And request-body (number)
  Then status 200
  And response-body (number)
""".trim()

        val request = HttpRequest(method = "POST", path = "/number", body = NumberValue(10))
        val response = HttpResponse(status = 200, body = "100")

        ContractFake(gherkin, listOf(MockScenario(request, response))).use { fake ->
            val postResponse = RestTemplate().postForEntity<String>(fake.endPoint + "/number", "10")
            assertThat(postResponse.body).isEqualTo("100")
        }
    }
}