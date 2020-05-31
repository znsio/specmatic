package run.qontract.stub

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.*
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.*
import run.qontract.mock.ScenarioStub

internal class ApiKtTest {
    @Test
    fun `given an expectation json with a key and one without, the request with the key should always match`() {
        val behaviour = Feature("""
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

        val mock1 = ScenarioStub(request1, HttpResponse.OK(1))
        val mock2 = ScenarioStub(request2, HttpResponse.OK(2))
        val contractInfo = listOf(Pair(behaviour, listOf(mock1, mock2)))
        val response = stubResponse(request2, contractInfo)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo(NumberValue(2))
    }

    @Test
    fun `given an expectation with optional header, a request without the header should match`() {
        val behaviour = Feature("""
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
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `given an expectation with optional header, a request with the header should match`() {
        val behaviour = Feature("""
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
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `should be able to setup a mock with an optional header`() {
        val behaviour = Feature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body (number)
        And request-header X-Optional-Header? (string)
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", mapOf("X-Optional-Header" to "some value"), NumberValue(10))
        val mock = ScenarioStub(request, HttpResponse.OK(10))

        val response = stubResponse(request, listOf(Pair(behaviour, listOf(mock))))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `should be able to setup a mock without an optional header`() {
        val behaviour = Feature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body (number)
        And request-header X-Optional-Header? (string)
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", emptyMap(), NumberValue(10))
        val mock = ScenarioStub(request, HttpResponse.OK(10))

        val response = stubResponse(request, listOf(Pair(behaviour, listOf(mock))))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `given a mock with fewer headers before a mock with more params, the mock with fewer should not eat up all requests with its subset of headers`() {
        val behaviour = Feature("""
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

        val mock1 = ScenarioStub(request1, HttpResponse.OK(1))
        val mock2 = ScenarioStub(request2, HttpResponse.OK(2))
        val contractInfo = listOf(Pair(behaviour, listOf(mock1, mock2)))
        val response = stubResponse(request2, contractInfo)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo(NumberValue(2))
    }

    @Test
    fun `given a mock with fewer query params before a mock with more params, the mock with fewer should not eat up all requests with its subset of headers`() {
        val behaviour = Feature("""
Feature: Math API

    Scenario: Some number
        When GET /number?param1=(string)&param2=(string)
        Then status 200
        And response-body (number)
""".trim())

        val request1 = HttpRequest("GET", "/number", queryParams = mapOf("param1" to "some value"))
        val request2 = HttpRequest("GET", "/number", queryParams = mapOf("param1" to "some value", "param2" to "some other value"))

        val mock1 = ScenarioStub(request1, HttpResponse.OK(1))
        val mock2 = ScenarioStub(request2, HttpResponse.OK(2))
        val contractInfo = listOf(Pair(behaviour, listOf(mock1, mock2)))
        val response = stubResponse(request2, contractInfo)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo(NumberValue(2))
    }

    @Test
    fun `stubbing out a contract pattern in json by specifing a sub pattern in stub data`() {
        val behaviour = Feature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body
          | number      | (number?)  |
        Then status 200
        And response-body (number)
""".trim())

        val mockRequest1 = HttpRequest("POST", "/square", body = JSONObjectValue(mapOf("number" to StringValue("(number)"))))
        val mockRequest2 = HttpRequest("POST", "/square", body = JSONObjectValue(mapOf("number" to StringValue("(null)"))))

        val mock1 = ScenarioStub(mockRequest1, HttpResponse.OK(NumberValue(1)))
        val mock2 = ScenarioStub(mockRequest2, HttpResponse.OK(NumberValue(2)))
        val contractInfo = listOf(Pair(behaviour, listOf(mock1, mock2)))

        val actualRequest1 = HttpRequest("POST", "/square", body = JSONObjectValue(mapOf("number" to NumberValue(10))))
        stubResponse(actualRequest1, contractInfo).let { response ->
            println(response)
            assertThat(response.status).isEqualTo(200)
            assertThat(response.body).isEqualTo(NumberValue(1))
        }

        val actualRequest2 = HttpRequest("POST", "/square", body = JSONObjectValue(mapOf("number" to NullValue)))
        stubResponse(actualRequest2, contractInfo).let { response ->
            println(response)
            assertThat(response.status).isEqualTo(200)
            assertThat(response.body).isEqualTo(NumberValue(2))
        }
    }

    @Test
    fun `stubbing out a contract pattern in request body by specifying a sub pattern in stub data`() {
        val behaviour = Feature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body (number?)
        Then status 200
        And response-body (number)
""".trim())

        val mockRequest1 = HttpRequest("POST", "/square", body = StringValue("(number)"))
        val mockRequest2 = HttpRequest("POST", "/square", body = StringValue("(null)"))

        val mock1 = ScenarioStub(mockRequest1, HttpResponse.OK(1))
        val mock2 = ScenarioStub(mockRequest2, HttpResponse.OK(2))
        val contractInfo = listOf(Pair(behaviour, listOf(mock1, mock2)))

        val actualRequest1 = HttpRequest("POST", "/square", body = NumberValue(10))
        stubResponse(actualRequest1, contractInfo).let { response ->
            println(response)
            assertThat(response.status).isEqualTo(200)
            assertThat(response.body).isEqualTo(NumberValue(1))
        }

        val actualRequest2 = HttpRequest("POST", "/square", body = EmptyString)
        stubResponse(actualRequest2, contractInfo).let { response ->
            println(response)
            assertThat(response.status).isEqualTo(200)
            assertThat(response.body).isEqualTo(NumberValue(2))
        }
    }

    @Test
    fun `should load a stub with a form content part and match such a request`() {
        val behaviour = Feature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-part number (number)
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", multiPartFormData = listOf(MultiPartContentValue("number", NumberValue(10))))
        val mock = ScenarioStub(request, HttpResponse.OK(10))

        val response = stubResponse(request, listOf(Pair(behaviour, listOf(mock))))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `should match multipart content part`() {
        val behaviour = Feature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-part number (number)
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", multiPartFormData = listOf(MultiPartContentValue("number", NumberValue(10))))

        val response = stubResponse(request, listOf(Pair(behaviour, emptyList())))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `should load a stub with a file part and match such a request`() {
        val behaviour = Feature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-part number @number.txt text/plain
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", multiPartFormData = listOf(MultiPartFileValue("number", "@number.txt", "text/plain", null)))
        val mock = ScenarioStub(request, HttpResponse.OK(10))

        val response = stubResponse(request, listOf(Pair(behaviour, listOf(mock))))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `should match multipart file part`() {
        val behaviour = Feature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-part number @number.txt text/plain
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", multiPartFormData = listOf(MultiPartFileValue("number", "@number.txt", "text/plain", null)))

        val response = stubResponse(request, listOf(Pair(behaviour, emptyList())))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    private fun fakeResponse(request: HttpRequest, behaviour: Feature): HttpResponse {
        return stubResponse(request, listOf(Pair(behaviour, emptyList())))
    }

    private fun stubResponse(request: HttpRequest, contractInfo: List<Pair<Feature, List<ScenarioStub>>>): HttpResponse {
        val expectations = contractInfoToExpectations(contractInfo)
        return stubResponse(request, contractInfo, expectations)
    }
}
