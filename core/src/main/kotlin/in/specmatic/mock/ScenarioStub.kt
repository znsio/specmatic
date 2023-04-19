package `in`.specmatic.mock

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.*

data class ScenarioStub(val request: HttpRequest = HttpRequest(), val response: HttpResponse = HttpResponse(0, emptyMap()), val kafkaMessage: KafkaMessage? = null, val delayInSeconds: Int? = null, val stubToken: String? = null, val requestBodyRegex: String? = null) {
    fun toJSON(): JSONObjectValue {
        val mockInteraction = mutableMapOf<String, Value>()
        if(kafkaMessage != null) {
            TODO("Implement serialisation")
        } else {
            mockInteraction[MOCK_HTTP_REQUEST] = request.toJSON()
            mockInteraction[MOCK_HTTP_RESPONSE] = response.toJSON()
        }

        return JSONObjectValue(mockInteraction)
    }
}

const val MOCK_KAFKA_MESSAGE = "kafka-message"
const val MOCK_HTTP_REQUEST = "http-request"
const val MOCK_HTTP_RESPONSE = "http-response"
const val DELAY_IN_SECONDS = "delay-in-seconds"
const val TRANSIENT_MOCK = "http-stub"
const val TRANSIENT_MOCK_ID = "$TRANSIENT_MOCK-id"
const val REQUEST_BODY_REGEX = "requestBodyRegex"

val MOCK_HTTP_REQUEST_ALL_KEYS = listOf("mock-http-request", MOCK_HTTP_REQUEST)
val MOCK_HTTP_RESPONSE_ALL_KEYS = listOf("mock-http-response", MOCK_HTTP_RESPONSE)

fun validateMock(mockSpec: Map<String, Any?>) {
    if (!mockSpec.containsKey(MOCK_KAFKA_MESSAGE)) {
        if (MOCK_HTTP_REQUEST_ALL_KEYS.none { mockSpec.containsKey(it) })
            throw ContractException(errorMessage = "Stub does not contain http-request/mock-http-request as a top level key.")
        if (MOCK_HTTP_RESPONSE_ALL_KEYS.none { mockSpec.containsKey(it) })
            throw ContractException(errorMessage = "Stub does not contain http-request/mock-http-request as a top level key.")
    }
}

fun mockFromJSON(mockSpec: Map<String, Value>): ScenarioStub {
    return when {
        mockSpec.contains(MOCK_KAFKA_MESSAGE) -> ScenarioStub(kafkaMessage = kafkaMessageFromJSON(getJSONObjectValue(MOCK_KAFKA_MESSAGE, mockSpec)))
        else -> {
            val mockRequest: HttpRequest = requestFromJSON(getJSONObjectValue(MOCK_HTTP_REQUEST_ALL_KEYS, mockSpec))
            val mockResponse: HttpResponse = HttpResponse.fromJSON(getJSONObjectValue(MOCK_HTTP_RESPONSE_ALL_KEYS, mockSpec))

            val delayInSeconds: Int? = getIntOrNull(DELAY_IN_SECONDS, mockSpec)
            val stubToken: String? = getStringOrNull(TRANSIENT_MOCK_ID, mockSpec)
            val requestBodyRegex: String? = getRequestBodyRegexOrNull(mockSpec)

            ScenarioStub(request = mockRequest, response = mockResponse, delayInSeconds = delayInSeconds, stubToken = stubToken, requestBodyRegex = requestBodyRegex)
        }
    }
}

fun getRequestBodyRegexOrNull(mockSpec: Map<String, Value>): String? {
    val requestSpec: Map<String, Value> = getJSONObjectValue(MOCK_HTTP_REQUEST_ALL_KEYS, mockSpec)
    return requestSpec[REQUEST_BODY_REGEX]?.toStringLiteral()
}

private const val KAFKA_TOPIC_KEY = "topic"
private const val KAFKA_VALUE_KEY = "value"
private const val KAFKA_KEY_KEY = "key"

fun kafkaMessageFromJSON(json: Map<String, Value>): KafkaMessage {
    if(KAFKA_TOPIC_KEY !in json)
        throw ContractException("Kafka message stub info must contain a topic name")

    if(KAFKA_VALUE_KEY !in json)
        throw ContractException("Kafka message stub info must contain a payload")

    val target = json.getValue(KAFKA_TOPIC_KEY)
    val key = json[KAFKA_KEY_KEY]
    val value = json.getValue(KAFKA_VALUE_KEY)

    return KafkaMessage(target.toStringLiteral(), key?.let { StringValue(it.toStringLiteral()) }, value)
}

fun getJSONObjectValue(keys: List<String>, mapData: Map<String, Value>): Map<String, Value> {
    val key = keys.first { mapData.containsKey(it) }
    return getJSONObjectValue(key, mapData)
}

fun getJSONObjectValue(key: String, mapData: Map<String, Value>): Map<String, Value> {
    val data = mapData.getValue(key)
    if(data !is JSONObjectValue) throw ContractException("$key should be a json object")
    return data.jsonObject
}

fun getIntOrNull(key: String, mapData: Map<String, Value>): Int? {
    val data = mapData[key]

    return data?.let {
        if(data !is NumberValue) throw ContractException("$key should be a number")
        return data.number.toInt()
    }
}

fun getStringOrNull(key: String, mapData: Map<String, Value>): String? {
    val data = mapData[key]

    return data?.let {
        if(data !is StringValue) throw ContractException("$key should be a number")
        return data.string
    }
}

