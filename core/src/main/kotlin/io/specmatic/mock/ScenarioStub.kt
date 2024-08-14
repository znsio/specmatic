package io.specmatic.mock

import io.specmatic.core.*
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.value.*
import io.specmatic.stub.stringToMockScenario

data class ScenarioStub(val request: HttpRequest = HttpRequest(), val response: HttpResponse = HttpResponse(0, emptyMap()), val delayInMilliseconds: Long? = null, val stubToken: String? = null, val requestBodyRegex: String? = null, val data: JSONObjectValue = JSONObjectValue()) {
    fun toJSON(): JSONObjectValue {
        val mockInteraction = mutableMapOf<String, Value>()

        mockInteraction[MOCK_HTTP_REQUEST] = request.toJSON()
        mockInteraction[MOCK_HTTP_RESPONSE] = response.toJSON()

        return JSONObjectValue(mockInteraction)
    }

    private fun combinations(data: Map<String, Map<String, Map<String, Value>>>): List<Map<String, Map<String, Map<String, Value>>>> {
        // Helper function to compute Cartesian product of multiple lists
        fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
            return lists.fold(listOf(listOf())) { acc, list ->
                acc.flatMap { item -> list.map { value -> item + value } }
            }
        }

        // Generate the Cartesian product of the values in the input map
        val product = cartesianProduct(data.map { (key, nestedMap) ->
            nestedMap.map { (nestedKey, valueMap) ->
                mapOf(key to mapOf(nestedKey to valueMap))
            }
        })

        // Convert each product result into a combined map
        return product.map { item ->
            item.reduce { acc, map -> acc + map }
        }
    }

    fun resolveDataSubstitutions(): List<ScenarioStub> {
        if(data.jsonObject.isEmpty())
            return listOf(this)

        val substitutions = unwrapSubstitutions(data)

        val combinations = combinations(substitutions)

        return combinations.map { combination ->
            replaceInExample(combination)
        }
    }

    private fun unwrapSubstitutions(rawSubstitutions: JSONObjectValue): Map<String, Map<String, Map<String, Value>>> {
        val substitutions = rawSubstitutions.jsonObject.mapValues {
            val json =
                it.value as? JSONObjectValue ?: throw ContractException("Invalid structure of data in the example file")

            json.jsonObject.mapValues {
                val innerJSON =
                    it.value as? JSONObjectValue ?: throw ContractException("Invalid structure of data in the example file")

                innerJSON.jsonObject.mapValues {
                    it.value
                }
            }
        }
        return substitutions
    }

    fun replaceInRequestBody(value: JSONObjectValue, substitutions: Map<String, Map<String, Map<String, Value>>>): Value {
        return value.copy(
            value.jsonObject.mapValues {
                replaceInRequestBody(it.value, substitutions)
            }
        )
    }

    fun replaceInRequestBody(value: JSONArrayValue, substitutions: Map<String, Map<String, Map<String, Value>>>): Value {
        return value.copy(
            value.list.map {
                replaceInRequestBody(value, substitutions)
            }
        )
    }

    fun replaceInRequestBody(value: Value, substitutions: Map<String, Map<String, Map<String, Value>>>): Value {
        return when(value) {
            is StringValue -> {
                if(value.string.startsWith("{{@") && value.string.endsWith("}}")) {
                    val substitutionSetName = value.string.removeSurrounding("{{", "}}")
                    val substitutionSet = substitutions[substitutionSetName] ?: throw ContractException("$substitutionSetName does not exist in the data")

                    val substitutionKey = substitutionSet.keys.firstOrNull() ?: throw ContractException("$substitutionSetName in data is empty")

                    StringValue(substitutionKey)
                } else
                    value
            }
            is JSONObjectValue -> {
                replaceInRequestBody(value, substitutions)
            }
            is JSONArrayValue -> {
                replaceInRequestBody(value, substitutions)
            }
            else -> value
        }
    }

    fun replaceInExample(substitutions: Map<String, Map<String, Map<String, Value>>>): ScenarioStub {
        val newRequestBody = replaceInRequestBody(request.body, substitutions)
        val newRequest = request.copy(body = newRequestBody)

        val newResponseBody = replaceInResponseBody(response.body, substitutions, "")
        val newResponse = response.copy(body = newResponseBody)

        return copy(
            request = newRequest,
            response = newResponse
        )
    }

    fun replaceInResponseBody(value: JSONObjectValue, substitutions: Map<String, Map<String, Map<String, Value>>>): Value {
        return value.copy(
            value.jsonObject.mapValues {
                replaceInResponseBody(it.value, substitutions, it.key)
            }
        )
    }

    fun replaceInResponseBody(value: JSONArrayValue, substitutions: Map<String, Map<String, Map<String, Value>>>): Value {
        return value.copy(
            value.list.map {
                replaceInResponseBody(value, substitutions)
            }
        )
    }

    fun replaceInResponseBody(value: Value, substitutions: Map<String, Map<String, Map<String, Value>>>, key: String): Value {
        return when(value) {
            is StringValue -> {
                if(value.string.startsWith("{{@") && value.string.endsWith("}}")) {
                    val substitutionSetName = value.string.removeSurrounding("{{", "}}")
                    val substitutionSet = substitutions[substitutionSetName] ?: throw ContractException("$substitutionSetName does not exist in the data")

                    val substitutionValue = substitutionSet.values.first()[key] ?: throw ContractException("$substitutionSetName does not contain a value for $key")

                    substitutionValue
                } else
                    value
            }
            is JSONObjectValue -> {
                replaceInResponseBody(value, substitutions)
            }
            is JSONArrayValue -> {
                replaceInResponseBody(value, substitutions)
            }
            else -> value
        }
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

    val data = getJSONObjectValueOrNull("data", mockSpec)?.let { JSONObjectValue(it) } ?: JSONObjectValue()

    val delayInSeconds: Int? = getIntOrNull(DELAY_IN_SECONDS, mockSpec)
    val delayInMilliseconds: Long? = getLongOrNull(DELAY_IN_MILLISECONDS, mockSpec)
    val delayInMs: Long? = delayInMilliseconds ?: delayInSeconds?.let { it.toLong().times(1000) }

    val stubToken: String? = getStringOrNull(TRANSIENT_MOCK_ID, mockSpec)
    val requestBodyRegex: String? = getRequestBodyRegexOrNull(mockSpec)

    return ScenarioStub(request = mockRequest, response = mockResponse, delayInMilliseconds = delayInMs, stubToken = stubToken, requestBodyRegex = requestBodyRegex, data = data)
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

fun getJSONObjectValueOrNull(key: String, mapData: Map<String, Value>): Map<String, Value>? {
    val data = mapData[key] ?: return null
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

