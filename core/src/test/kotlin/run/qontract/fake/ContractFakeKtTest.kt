package run.qontract.fake

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import run.qontract.core.ContractBehaviour
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.EmptyString
import run.qontract.core.value.NumberValue
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

    @Test
    fun `given an expectation with optional header, a request without the header should match`() {
        val behaviour = ContractBehaviour("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body (number)
        And request-header X-Optional-Header? (string)
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", emptyMap(), NumberValue(10))
        val response = fakeResponse(request, behaviour)

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).hasSizeGreaterThan(0)
        assertDoesNotThrow { response.body?.toInt() }
    }

    @Test
    fun `given an expectation with optional header, a request with the header should match`() {
        val behaviour = ContractBehaviour("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body (number)
        And request-header X-Optional-Header? (string)
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", mapOf("X-Optional-Header" to "some value"), NumberValue(10))
        val response = fakeResponse(request, behaviour)

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).hasSizeGreaterThan(0)
        assertDoesNotThrow { response.body?.toInt() }
    }

    @Test
    fun `should be able to setup a mock with an optional header`() {
        val behaviour = ContractBehaviour("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body (number)
        And request-header X-Optional-Header? (string)
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", mapOf("X-Optional-Header" to "some value"), NumberValue(10))
        val mock = MockScenario(request, HttpResponse.OK("10"))

        val response = stubResponse(request, listOf(Pair(behaviour, listOf(mock))))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).hasSizeGreaterThan(0)
        assertDoesNotThrow { response.body?.toInt() }
    }

    @Test
    fun `should be able to setup a mock without an optional header`() {
        val behaviour = ContractBehaviour("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body (number)
        And request-header X-Optional-Header? (string)
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", emptyMap(), NumberValue(10))
        val mock = MockScenario(request, HttpResponse.OK("10"))

        val response = stubResponse(request, listOf(Pair(behaviour, listOf(mock))))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).hasSizeGreaterThan(0)
        assertDoesNotThrow { response.body?.toInt() }
    }

    @Test
    fun `given a mock with fewer headers before a mock with more params, the mock with fewer should not eat up all requests with its subset of headers`() {
        val behaviour = ContractBehaviour("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body (number)
        And request-header X-Required (string)
        And request-header X-Optional? (string)
        Then status 200
        And response-body (number)
""".trim())

        val request1 = HttpRequest("POST", "/square", mapOf("X-Required" to "some value"), parsedValue("""10"""))
        val request2 = HttpRequest("POST", "/square", mapOf("X-Required" to "some value", "X-Optional" to "some other value"), parsedValue("""10"""))

        val mock1 = MockScenario(request1, HttpResponse.OK("1"))
        val mock2 = MockScenario(request2, HttpResponse.OK("2"))
        val contractInfo = listOf(Pair(behaviour, listOf(mock1, mock2)))
        val response = stubResponse(request2, contractInfo)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo("2")
    }

    @Test
    fun `given a mock with fewer query params before a mock with more params, the mock with fewer should not eat up all requests with its subset of headers`() {
        val behaviour = ContractBehaviour("""
Feature: Math API

    Scenario: Some number
        When GET /number?param1=(string)&param2=(string)
        Then status 200
        And response-body (number)
""".trim())

        val request1 = HttpRequest("GET", "/number", queryParams = mapOf("param1" to "some value"))
        val request2 = HttpRequest("GET", "/number", queryParams = mapOf("param1" to "some value", "param2" to "some other value"))

        val mock1 = MockScenario(request1, HttpResponse.OK("1"))
        val mock2 = MockScenario(request2, HttpResponse.OK("2"))
        val contractInfo = listOf(Pair(behaviour, listOf(mock1, mock2)))
        val response = stubResponse(request2, contractInfo)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo("2")
    }

    private fun fakeResponse(request: HttpRequest, behaviour: ContractBehaviour): HttpResponse {
        return stubResponse(request, listOf(Pair(behaviour, emptyList())))
    }

    private fun stubResponse(request: HttpRequest, contractInfo: List<Pair<ContractBehaviour, List<MockScenario>>>): HttpResponse {
        val expectations = contractInfoToExpectations(contractInfo)
        return stubResponse(request, contractInfo, expectations)
    }
}
