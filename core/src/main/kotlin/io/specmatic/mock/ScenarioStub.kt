package io.specmatic.mock

import io.specmatic.core.*
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.value.*
import io.specmatic.stub.stringToMockScenario

data class ScenarioStub(val request: HttpRequest = HttpRequest(), val response: HttpResponse = HttpResponse(0, emptyMap()), val delayInMilliseconds: Long? = null, val stubToken: String? = null, val requestBodyRegex: String? = null) {
    fun toJSON(): JSONObjectValue {
        val mockInteraction = mutableMapOf<String, Value>()

        mockInteraction[MOCK_HTTP_REQUEST] = request.toJSON()
        mockInteraction[MOCK_HTTP_RESPONSE] = response.toJSON()

        return JSONObjectValue(mockInteraction)
    }

    companion object {
        fun parse(text: String): ScenarioStub {
            return stringToMockScenario(StringValue(text))
        }
    }
}

const val MOCK_HTTP_REQUEST = "http-request"
const val MOCK_HTTP_RESPONSE = "http-response"
const val DELAY_IN_SECONDS = "delay-in-seconds"
const val DELAY_IN_MILLISECONDS = "delay-in-milliseconds"
const val TRANSIENT_MOCK = "http-stub"
const val TRANSIENT_MOCK_ID = "$TRANSIENT_MOCK-id"
const val REQUEST_BODY_REGEX = "bodyRegex"

val MOCK_HTTP_REQUEST_ALL_KEYS = listOf("mock-http-request", MOCK_HTTP_REQUEST)
val MOCK_HTTP_RESPONSE_ALL_KEYS = listOf("mock-http-response", MOCK_HTTP_RESPONSE)

fun validateMock(mockSpec: Map<String, Any?>) {
    if (MOCK_HTTP_REQUEST_ALL_KEYS.none { mockSpec.containsKey(it) })
        throw ContractException(errorMessage = "Stub does not contain http-request/mock-http-request as a top level key.")
    if (MOCK_HTTP_RESPONSE_ALL_KEYS.none { mockSpec.containsKey(it) })
        throw ContractException(errorMessage = "Stub does not contain http-request/mock-http-request as a top level key.")
}

fun mockFromJSON(mockSpec: Map<String, Value>): ScenarioStub {
    val mockRequest: HttpRequest = requestFromJSON(getJSONObjectValue(MOCK_HTTP_REQUEST_ALL_KEYS, mockSpec))
    val mockResponse: HttpResponse = HttpResponse.fromJSON(getJSONObjectValue(MOCK_HTTP_RESPONSE_ALL_KEYS, mockSpec))

    val delayInSeconds: Int? = getIntOrNull(DELAY_IN_SECONDS, mockSpec)
    val delayInMilliseconds: Long? = getLongOrNull(DELAY_IN_MILLISECONDS, mockSpec)
    val delayInMs: Long? = delayInMilliseconds ?: delayInSeconds?.toLong()?.times(1000)

    val stubToken: String? = getStringOrNull(TRANSIENT_MOCK_ID, mockSpec)
    val requestBodyRegex: String? = getRequestBodyRegexOrNull(mockSpec)

    return ScenarioStub(request = mockRequest, response = mockResponse, delayInMilliseconds = delayInMs, stubToken = stubToken, requestBodyRegex = requestBodyRegex)
}

fun getRequestBodyRegexOrNull(mockSpec: Map<String, Value>): String? {
    val requestSpec: Map<String, Value> = getJSONObjectValue(MOCK_HTTP_REQUEST_ALL_KEYS, mockSpec)
    return requestSpec[REQUEST_BODY_REGEX]?.toStringLiteral()
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

fun getLongOrNull(key: String, mapData: Map<String, Value>): Long? {
    val data = mapData[key]

    return data?.let {
        if(data !is NumberValue) throw ContractException("$key should be a number")
        return data.number.toLong()
    }
}

fun getStringOrNull(key: String, mapData: Map<String, Value>): String? {
    val data = mapData[key]

    return data?.let {
        if(data !is StringValue) throw ContractException("$key should be a number")
        return data.string
    }
}

