package run.qontract.fake

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.ContractBehaviour
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.pattern.parsedValue
import run.qontract.mock.MockScenario

internal class ContractFakeKtTest {
    @Test
    fun `given an expectation json with a key and one without, the request with the key should always match`() {
        val behaviour = ContractBehaviour("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body
            | number       | (number) |
            | description? | (number in string) |
        Then status 200
        And response-body (number)
""".trim())

        val request1 = HttpRequest("POST", "/square", emptyMap(), parsedValue("""{"number": 10}"""))
        val request2 = HttpRequest("POST", "/square", emptyMap(), parsedValue("""{"number": 10, "description": "10"}"""))

        val mock1 = MockScenario(request1, HttpResponse.OK("1"))
        val mock2 = MockScenario(request2, HttpResponse.OK("2"))
        val contractInfo = listOf(Pair(behaviour, listOf(mock1, mock2)))
        val response = stubResponse(request2, contractInfo)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo("2")
    }

    private fun stubResponse(request: HttpRequest, contractInfo: List<Pair<ContractBehaviour, List<MockScenario>>>): HttpResponse {
        val expectations = contractInfoToExpectations(contractInfo)
        return stubResponse(request, contractInfo, expectations)
    }
}
