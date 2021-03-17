package `in`.specmatic.stub

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.value.*
import `in`.specmatic.mock.ScenarioStub
import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal class ApiKtTest {
    @Test
    fun `given an expectation json with a key and one without, the request with the key should always match`() {
        val behaviour = parseGherkinStringToFeature("""
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
        val behaviour = parseGherkinStringToFeature("""
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
        val behaviour = parseGherkinStringToFeature("""
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
        val behaviour = parseGherkinStringToFeature("""
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
        val behaviour = parseGherkinStringToFeature("""
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
        val behaviour = parseGherkinStringToFeature("""
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
        val behaviour = parseGherkinStringToFeature("""
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
    fun `stubbing out a contract pattern in json by specifying a sub pattern in stub data`() {
        val behaviour = parseGherkinStringToFeature("""
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
        val behaviour = parseGherkinStringToFeature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-body (number?)
        Then status 200
        And response-body (number)
""".trim())

        val mockRequest1 = HttpRequest("POST", "/square", body = StringValue("(number)"))
        val mockRequest2 = HttpRequest("POST", "/square", body = StringValue("(empty)"))

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
        val behaviour = parseGherkinStringToFeature("""
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
        val behaviour = parseGherkinStringToFeature("""
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
        val behaviour = parseGherkinStringToFeature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-part number @number.txt text/plain
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", multiPartFormData = listOf(MultiPartFileValue("number", "number.txt", "text/plain", null)))
        val mock = ScenarioStub(request, HttpResponse.OK(10))

        val response = stubResponse(request, listOf(Pair(behaviour, listOf(mock))))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `should match multipart file part`() {
        val feature = parseGherkinStringToFeature("""
Feature: Math API

    Scenario: Square of a number
        When POST /square
        And request-part number @number.txt text/plain
        Then status 200
        And response-body (number)
""".trim())

        val request = HttpRequest("POST", "/square", multiPartFormData = listOf(MultiPartFileValue("number", "number.txt", "text/plain", null)))

        val response = stubResponse(request, listOf(Pair(feature, emptyList())))

        println(response.toLogString())
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isInstanceOf(NumberValue::class.java)
    }

    @Test
    fun `should get a stub out of a qontract and a matching stub file`() {
        val feature = parseGherkinStringToFeature("""
Feature: Math API
    Scenario: Square of a number
        When POST /square
        And request-body (number)
        Then status 200
        And response-body (number)
""".trim())

        val stubInfo = loadQontractStubs(listOf(Pair("math.$CONTRACT_EXTENSION", feature)), listOf(Pair("sample.json", ScenarioStub(HttpRequest(method = "POST", path = "/square", body = StringValue("10")), HttpResponse(status = 200, body = "20")))))
        assertThat(stubInfo.single().first).isEqualTo(feature)

        val stub = stubInfo.single().second.single()
        val expectedRequest = HttpRequest("POST", path = "/square", body = StringValue("10"))
        val expectedResponse = HttpResponse(status = 200, body = StringValue("20"))

        assertThat(stub.request).isEqualTo(expectedRequest)
        assertThat(stub.response).isEqualTo(expectedResponse)
    }

    @Test
    fun `qontract should reject a stub file that does not match the qontract`() {
        val feature = parseGherkinStringToFeature("""
Feature: Math API
    Scenario: Square of a number
        When POST /square
        And request-body (number)
        Then status 200
        And response-body (number)
""".trim())

        val (stdout, stubInfo) =  captureStandardOutput {
            loadQontractStubs(listOf(Pair("math.$CONTRACT_EXTENSION", feature)), listOf(Pair("sample.json", ScenarioStub(HttpRequest(method = "POST", path = "/square", body = StringValue("10")), HttpResponse(status = 200, body = "not a number")))))
        }

        assertThat(stubInfo.single().first).isEqualTo(feature)
        assertThat(stubInfo.single().second).isEmpty()

        val expectedOnStandardOutput = """sample.json didn't match math.$CONTRACT_EXTENSION
    In scenario "Square of a number"
    >> RESPONSE.BODY
  
    Expected number, actual was string: "not a number""""

        assertThat(stdout).contains(expectedOnStandardOutput)
    }

    @Test
    fun `should get a stub out of two qontracts and a single stub file that matches only one of them`() {
        val squareFeature = parseGherkinStringToFeature("""
Feature: Square API
    Scenario: Square of a number
        When POST /square
        And request-body (number)
        Then status 200
        And response-body (number)
""".trim())

        val cubeFeature = parseGherkinStringToFeature("""
Feature: Cube API
    Scenario: Cube of a number
        When POST /cube
        And request-body (number)
        Then status 200
        And response-body (number)
""".trim())

        val features = listOf(Pair("square.$CONTRACT_EXTENSION", squareFeature), Pair("cube.$CONTRACT_EXTENSION", cubeFeature))
        val rawStubInfo = listOf(Pair("sample.json", ScenarioStub(HttpRequest(method = "POST", path = "/square", body = StringValue("10")), HttpResponse(status = 200, body = "20"))))
        val stubInfo = loadQontractStubs(features, rawStubInfo)
        assertThat(stubInfo.map { it.first }).contains(squareFeature)
        assertThat(stubInfo.map { it.first }).contains(cubeFeature)
        assertThat(stubInfo).hasSize(2)

        val squareStub = stubInfo.first { it.first == squareFeature }

        val expectedRequest = HttpRequest("POST", path = "/square", body = StringValue("10"))
        val expectedResponse = HttpResponse(status = 200, body = StringValue("20"))

        assertThat(squareStub.second.single().request).isEqualTo(expectedRequest)
        assertThat(squareStub.second.single().response).isEqualTo(expectedResponse)
    }

    @Test
    fun `should not be able to load a stub json with unexpected keys in the request`() {
        val feature = parseGherkinStringToFeature("""
Feature: Math API
    Scenario: Square of a number
        When POST /square
        And request-body
        | number | (number) |
        Then status 200
        And response-body (number)
""".trim())

        val (output, stubInfo) = captureStandardOutput { loadQontractStubs(listOf(Pair("math.$CONTRACT_EXTENSION", feature)), listOf(Pair("sample.json", ScenarioStub(HttpRequest(method = "POST", path = "/square", body = StringValue("""{"number": 10, "unexpected": "data"}""")), HttpResponse(status = 200, body = "20"))))) }
        assertThat(stubInfo.single().first).isEqualTo(feature)
        assertThat(stubInfo.single().second).isEmpty()
        assertThat(output).contains("""sample.json didn't match math.$CONTRACT_EXTENSION
    In scenario "Square of a number"
    >> REQUEST.BODY
  
    Key named "unexpected" was unexpected""")
    }

    @Test
    fun `should not be able to load a stub json with unexpected keys in the response`() {
        val feature = parseGherkinStringToFeature("""
Feature: Math API
    Scenario: Square of a number
        When POST /square
        And request-body (number)
        Then status 200
        And response-body
        | number | (number) |
""".trim())

        val (output, stubInfo) = captureStandardOutput {  loadQontractStubs(listOf(Pair("math.$CONTRACT_EXTENSION", feature)), listOf(Pair("sample.json", ScenarioStub(HttpRequest(method = "POST", path = "/square", body = StringValue("""10""")), HttpResponse(status = 200, body = """{"number": 10, "unexpected": "data"}"""))))) }
        assertThat(stubInfo.single().first).isEqualTo(feature)
        assertThat(stubInfo.single().second).isEmpty()

        assertThat(output).contains("""sample.json didn't match math.$CONTRACT_EXTENSION
    In scenario "Square of a number"
    >> RESPONSE.BODY
  
    Key named "unexpected" was unexpected""")
    }

    @Test
    fun `should get a stub out of a qontract and a matching kafka stub file`() {
        val feature = parseGherkinStringToFeature("""
Feature: Customer Data
    Scenario: Send customer info
        * kafka-message customers (string)
""".trim())

        val stubInfo = loadQontractStubs(listOf(Pair("customers.$CONTRACT_EXTENSION", feature)), listOf(Pair("sample.json", ScenarioStub(kafkaMessage = KafkaMessage("customers", value = StringValue("some data"))))))
        assertThat(stubInfo.single().first).isEqualTo(feature)

        val stub = stubInfo.single().second.single()
        val expectedMessage = KafkaMessage("customers", value = StringValue("some data"))

        assertThat(stub.kafkaMessage).isEqualTo(expectedMessage)
    }

    private fun fakeResponse(request: HttpRequest, behaviour: Feature): HttpResponse {
        return stubResponse(request, listOf(Pair(behaviour, emptyList())))
    }

    private fun stubResponse(request: HttpRequest, contractInfo: List<Pair<Feature, List<ScenarioStub>>>): HttpResponse {
        val expectations = contractInfoToExpectations(contractInfo)
        return stubResponse(request, contractInfo, expectations)
    }
}

fun <ReturnType> captureStandardOutput(fn: () -> ReturnType): Pair<String, ReturnType> {
    val originalOut = System.out

    val byteArrayOutputStream = ByteArrayOutputStream()
    val newOut = PrintStream(byteArrayOutputStream)
    System.setOut(newOut)

    val result = fn()

    System.out.flush()
    System.setOut(originalOut) // So you can print again
    return Pair(String(byteArrayOutputStream.toByteArray()).trim(), result)
}

fun contractInfoToExpectations(contractInfo: List<Pair<Feature, List<ScenarioStub>>>): StubDataItems {
    return contractInfo.fold(StubDataItems()) { stubsAcc, (feature, mocks) ->
        val newStubs = mocks.fold(StubDataItems()) { stubs, mock ->
            val kafkaMessage = mock.kafkaMessage
            if(kafkaMessage != null) {
                StubDataItems(stubs.http, stubs.kafka.plus(KafkaStubData(kafkaMessage)))
            } else {
                val stubData = feature.matchingStub(mock)
                StubDataItems(stubs.http.plus(stubData), stubs.kafka)
            }
        }

        StubDataItems(stubsAcc.http.plus(newStubs.http), stubsAcc.kafka.plus(newStubs.kafka))
    }
}
