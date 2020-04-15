package run.qontract.mock

import run.qontract.core.ContractBehaviour
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.pattern.ContractException
import run.qontract.core.requestFromJSON
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value

data class MockScenario(val request: HttpRequest = HttpRequest(), val response: HttpResponse = HttpResponse()) {
    @Throws(MockException::class)
    fun toJSON(): MutableMap<String, Any> {
        val mockInteraction = mutableMapOf<String, Any>()
        mockInteraction[MOCK_HTTP_REQUEST] = request.toJSON()
        mockInteraction[MOCK_HTTP_RESPONSE] = response.toJSON()
        return mockInteraction
    }
}

private const val MOCK_HTTP_REQUEST = "mock-http-request"
private const val MOCK_HTTP_RESPONSE = "mock-http-response"

fun validateMock(mockSpec: Map<String, Any?>) {
    if (!mockSpec.containsKey(MOCK_HTTP_REQUEST)) throw ContractException(errorMessage = "This spec does not information about the request to be mocked.")
    if (!mockSpec.containsKey(MOCK_HTTP_RESPONSE)) throw ContractException(errorMessage = "This spec does not information about the response to be mocked.")
}

fun mockFromJSON(mockSpec: Map<String, Value>): MockScenario {
    val mockRequest = requestFromJSON(getJSONObjectValue(MOCK_HTTP_REQUEST, mockSpec))
    val mockResponse = HttpResponse.fromJSON(getJSONObjectValue(MOCK_HTTP_RESPONSE, mockSpec))

    return MockScenario(mockRequest, mockResponse)
}

private fun getJSONObjectValue(key: String, mapData: Map<String, Value>): Map<String, Value> {
    val data = mapData.getValue(key)
    if(data !is JSONObjectValue) throw ContractException("$key should be a json object")
    return data.jsonObject
}
