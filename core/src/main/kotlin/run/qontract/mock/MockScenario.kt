package run.qontract.mock

import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.requestFromJSON
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value

data class MockScenario(val request: HttpRequest = HttpRequest(), val response: HttpResponse = HttpResponse(), val facts: MutableMap<String, Value> = mutableMapOf()) {
    @Throws(HttpMockException::class)
    fun toJSON(): MutableMap<String, Any> {
        val mockInteraction = mutableMapOf<String, Any>()
        mockInteraction[MOCK_HTTP_REQUEST] = request.toJSON()
        mockInteraction[MOCK_HTTP_RESPONSE] = response.toJSON()
        if (facts.isNotEmpty()) mockInteraction[MOCK_FACTS] = facts
        return mockInteraction
    }

    fun addFact(name: String, value: Value) {
        facts[name] = value
    }
}

private const val MOCK_HTTP_REQUEST = "mock-http-request"
private const val MOCK_HTTP_RESPONSE = "mock-http-response"
private const val MOCK_FACTS = "mock-facts"

fun validateMock(mockSpec: Map<String, Any?>) {
    if (!mockSpec.containsKey(MOCK_HTTP_REQUEST)) throw HttpMockException("This spec does not information about the request to be mocked.")
    if (!mockSpec.containsKey(MOCK_HTTP_RESPONSE)) throw HttpMockException("This spec does not information about the response to be mocked.")
}

fun mockFromJSON(mockSpec: Map<String, Value>): MockScenario {
    val mockRequestSpec = mockSpec.getValue(MOCK_HTTP_REQUEST) as JSONObjectValue
    val mockRequest = requestFromJSON(mockRequestSpec.jsonObject)
    val mockResponseSpec = mockSpec.getValue(MOCK_HTTP_RESPONSE) as JSONObjectValue
    val mockResponse = HttpResponse.fromJSON(mockResponseSpec.jsonObject)

    val mockFacts: Map<String, Value> = if (mockSpec.containsKey(MOCK_FACTS)) {
        (mockSpec.getValue(MOCK_FACTS) as JSONObjectValue).jsonObject
    } else emptyMap()

    return MockScenario(mockRequest, mockResponse, mockFacts.toMutableMap())
}
