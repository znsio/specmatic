package run.qontract.mock

import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

data class MockScenario(val request: HttpRequest = HttpRequest(), val response: HttpResponse = HttpResponse(0, emptyMap()), val kafkaMessage: KafkaMessage? = null) {
    @Throws(MockException::class)
    fun toJSON(): MutableMap<String, Any> {
        val mockInteraction = mutableMapOf<String, Any>()
        if(kafkaMessage != null) {
            TODO("Implement serialisation")
        } else {
            mockInteraction[MOCK_HTTP_REQUEST] = request.toJSON()
            mockInteraction[MOCK_HTTP_RESPONSE] = response.toJSON()
        }
        return mockInteraction
    }
}

private const val MOCK_KAFKA_MESSAGE = "message"
private const val MOCK_HTTP_REQUEST = "mock-http-request"
private const val MOCK_HTTP_RESPONSE = "mock-http-response"

fun validateMock(mockSpec: Map<String, Any?>) {
    if (!mockSpec.containsKey(MOCK_KAFKA_MESSAGE)) {
        if (!mockSpec.containsKey(MOCK_HTTP_REQUEST)) throw ContractException(errorMessage = "This spec does contain not information about the request to be mocked.")
        if (!mockSpec.containsKey(MOCK_HTTP_RESPONSE)) throw ContractException(errorMessage = "This spec does not contain information about the response to be mocked.")
    }
}

fun mockFromJSON(mockSpec: Map<String, Value>): MockScenario {
    return when {
        mockSpec.contains(MOCK_KAFKA_MESSAGE) -> MockScenario(kafkaMessage = messageFromJSON(getJSONObjectValue(MOCK_KAFKA_MESSAGE, mockSpec)))
        else -> {
            val mockRequest = requestFromJSON(getJSONObjectValue(MOCK_HTTP_REQUEST, mockSpec))
            val mockResponse = HttpResponse.fromJSON(getJSONObjectValue(MOCK_HTTP_RESPONSE, mockSpec))

            MockScenario(request = mockRequest, response = mockResponse)
        }
    }
}

fun messageFromJSON(json: Map<String, Value>): KafkaMessage {
    if("target" !in json)
        throw ContractException("Async message stub info must contain a target (queue / topic name)")

    if("value" !in json)
        throw ContractException("Async message stub info must contain a payload")

    val target = json.getValue("target")
    val key = json.get("key")
    val value = json.getValue("value")

    return KafkaMessage(target.toStringValue(), key?.let { StringValue(it.toStringValue()) }, value)
}

private fun getJSONObjectValue(key: String, mapData: Map<String, Value>): Map<String, Value> {
    val data = mapData.getValue(key)
    if(data !is JSONObjectValue) throw ContractException("$key should be a json object")
    return data.jsonObject
}
