package io.specmatic.mock

import io.specmatic.core.*
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.Pattern
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.utilities.Flags
import io.specmatic.core.value.*
import io.specmatic.stub.stringToMockScenario
import java.io.File

fun loadDictionary(): Map<String,Value> {
    val configFileName = getConfigFileName()
    val configuredDictionary = if(File(configFileName).exists()) loadSpecmaticConfig(configFileName).stub.dictionary else null

    val fileName = Flags.getStringValue(SPECMATIC_STUB_DICTIONARY) ?: configuredDictionary ?: return emptyMap()
    return parsedJSONObject(File(fileName).readText()).jsonObject
}

data class ScenarioStub(
    val request: HttpRequest = HttpRequest(),
    val response: HttpResponse = HttpResponse(0, emptyMap()),
    val delayInMilliseconds: Long? = null,
    val stubToken: String? = null,
    val requestBodyRegex: String? = null,
    val data: JSONObjectValue = JSONObjectValue(),
    val filePath: String? = null,
    val partial: ScenarioStub? = null,
    val dictionary: Map<String, Value> = emptyMap()
) {
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

    fun findPatterns(input: String): Set<String> {
        val pattern = """\{\{(@\w+)\}\}""".toRegex()
        return pattern.findAll(input).map { it.groupValues[1] }.toSet()
    }

    fun dataTemplateNameOnly(wholeDataTemplateName: String): String {
        return wholeDataTemplateName.split(".").first()
    }

    fun requestDataTemplates(): Set<String> {
        return findPatterns(request.toLogString()).map(this::dataTemplateNameOnly).toSet()
    }

    fun responseDataTemplates(): Set<String> {
        return findPatterns(response.toLogString()).map(this::dataTemplateNameOnly).toSet()
    }

    fun resolveDataSubstitutions(scenario: Scenario): List<ScenarioStub> {
        return listOf(this)
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

    private fun replaceInRequestBody(value: JSONObjectValue, substitutions: Map<String, Map<String, Map<String, Value>>>, requestTemplatePatterns: Map<String, Pattern>, resolver: Resolver): Value {
        return value.copy(
            value.jsonObject.mapValues {
                replaceInRequestBody(it.key, it.value, substitutions, requestTemplatePatterns, resolver)
            }
        )
    }

    private fun replaceInRequestBody(value: JSONArrayValue, substitutions: Map<String, Map<String, Map<String, Value>>>, requestTemplatePatterns: Map<String, Pattern>, resolver: Resolver): Value {
        return value.copy(
            value.list.map {
                replaceInRequestBody(value, substitutions, requestTemplatePatterns, resolver)
            }
        )
    }

    private fun substituteStringInRequest(value: String, substitutions: Map<String, Map<String, Map<String, Value>>>): String {
        return if(value.hasDataTemplate()) {
            val substitutionSetName = value.removeSurrounding("{{", "}}")
            val substitutionSet = substitutions[substitutionSetName] ?: throw ContractException("$substitutionSetName does not exist in the data")

            substitutionSet.keys.firstOrNull() ?: throw ContractException("$substitutionSetName in data is empty")
        } else
            value
    }

    private fun replaceInRequestBody(key: String, value: Value, substitutions: Map<String, Map<String, Map<String, Value>>>, requestTemplatePatterns: Map<String, Pattern>, resolver: Resolver): Value {
        return when(value) {
            is StringValue -> {
                if(value.hasDataTemplate()) {
                    val substitutionSetName = value.string.removeSurrounding("{{", "}}")
                    val substitutionSet = substitutions[substitutionSetName] ?: throw ContractException("$substitutionSetName does not exist in the data")

                    val substitutionKey = substitutionSet.keys.firstOrNull() ?: throw ContractException("$substitutionSetName in data is empty")

                    val pattern = requestTemplatePatterns.getValue(key)

                    pattern.parse(substitutionKey, resolver)
                } else
                    value
            }
            is JSONObjectValue -> {
                replaceInRequestBody(value, substitutions, requestTemplatePatterns, resolver)
            }
            is JSONArrayValue -> {
                replaceInRequestBody(value, substitutions, requestTemplatePatterns, resolver)
            }
            else -> value
        }
    }

    private fun replaceInExample(substitutions: Map<String, Map<String, Map<String, Value>>>, requestBody: Pattern, resolver: Resolver): ScenarioStub {
        val requestTemplatePatterns = requestBody.getTemplateTypes("", request.body, resolver).value

        val newPath = replaceInPath(request.path ?: "", substitutions)
        val newRequestHeaders = replaceInRequestHeaders(request.headers, substitutions)
        val newQueryParams: Map<String, String> = replaceInRequestQueryParams(request.queryParams, substitutions)
        val newRequestBody = replaceInRequestBody("", request.body, substitutions, requestTemplatePatterns, resolver)

        val newRequest = request.copy(
            path = newPath,
            headers = newRequestHeaders,
            queryParams = QueryParameters(newQueryParams),
            body = newRequestBody)

        val newResponseBody = replaceInResponseBody(response.body, substitutions, "")
        val newResponseHeaders = replaceInResponseHeaders(response.headers, substitutions)
        val newResponse = response.copy(
            headers = newResponseHeaders,
            body = newResponseBody
        )

        return copy(
            request = newRequest,
            response = newResponse
        )
    }

    private fun replaceInPath(path: String, substitutions: Map<String, Map<String, Map<String, Value>>>): String {
        val rawPathSegments = path.split("/")
        val pathSegments = rawPathSegments.let { if(it.firstOrNull() == "") it.drop(1) else it }
        val updatedSegments = pathSegments.map { if(it.hasDataTemplate()) substituteStringInRequest(it, substitutions) else it }
        val prefix = if(pathSegments.size != rawPathSegments.size) listOf("") else emptyList()

        return (prefix + updatedSegments).joinToString("/")
    }

    private fun replaceInResponseHeaders(
        headers: Map<String, String>,
        substitutions: Map<String, Map<String, Map<String, Value>>>
    ): Map<String, String> {
        return headers.mapValues { (key, value) ->
            substituteStringInResponse(value, substitutions, key)
        }
    }

    private fun replaceInRequestQueryParams(
        queryParams: QueryParameters,
        substitutions: Map<String, Map<String, Map<String, Value>>>
    ): Map<String, String> {
        return queryParams.asMap().mapValues { (key, value) ->
            substituteStringInRequest(value, substitutions)
        }
    }

    private fun replaceInRequestHeaders(headers: Map<String, String>, substitutions: Map<String, Map<String, Map<String, Value>>>): Map<String, String> {
        return headers.mapValues { (key, value) ->
            substituteStringInRequest(value, substitutions)
        }
    }

    private fun replaceInResponseBody(value: JSONObjectValue, substitutions: Map<String, Map<String, Map<String, Value>>>): Value {
        return value.copy(
            value.jsonObject.mapValues {
                replaceInResponseBody(it.value, substitutions, it.key)
            }
        )
    }

    private fun replaceInResponseBody(value: JSONArrayValue, substitutions: Map<String, Map<String, Map<String, Value>>>): Value {
        return value.copy(
            value.list.map { item: Value ->
                replaceInResponseBody(item, substitutions, "")
            }
        )
    }

    private fun substituteStringInResponse(value: String, substitutions: Map<String, Map<String, Map<String, Value>>>, key: String): String {
        return if(value.hasDataTemplate()) {
            val dataSetIdentifiers = DataSetIdentifiers(value, key)

            val substitutionSet = substitutions[dataSetIdentifiers.name] ?: throw ContractException("${dataSetIdentifiers.name} does not exist in the data")

            val substitutionValue = substitutionSet.values.first()[dataSetIdentifiers.key] ?: throw ContractException("${dataSetIdentifiers.name} does not contain a value for ${dataSetIdentifiers.key}")

            substitutionValue.toStringLiteral()
        } else
            value
    }

    class DataSetIdentifiers(rawSetName: String, objectKey: String) {
        val name: String
        val key: String

        init {
            val substitutionSetPieces = rawSetName.removeSurrounding("{{", "}}").split(".")

            name = substitutionSetPieces.getOrNull(0) ?: throw ContractException("Substitution set name {{}} was empty")
            key = substitutionSetPieces.getOrNull(1) ?: objectKey
        }
    }

    private fun replaceInResponseBody(value: Value, substitutions: Map<String, Map<String, Map<String, Value>>>, key: String): Value {
        return when(value) {
            is StringValue -> {
                if(value.hasDataTemplate()) {
                    val dataSetIdentifiers = DataSetIdentifiers(value.string, key)

                    val substitutionSet = substitutions[dataSetIdentifiers.name] ?: throw ContractException("${dataSetIdentifiers.name} does not exist in the data")

                    val substitutionValue = substitutionSet.values.first()[dataSetIdentifiers.key] ?: throw ContractException("${dataSetIdentifiers.name} does not contain a value for ${dataSetIdentifiers.key}")

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

        fun readFromFile(file: File): ScenarioStub {
            return stringToMockScenario(StringValue(file.readText(Charsets.UTF_8))).copy(filePath = file.path)
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

const val PARTIAL = "partial"

fun validateMock(mockSpec: Map<String, Any?>) {
    if(mockSpec.containsKey(PARTIAL)) {
        val template = mockSpec.getValue(PARTIAL) as? JSONObjectValue ?: throw ContractException("template should be an object")
        return validateMock(template.jsonObject)
    }

    if (MOCK_HTTP_REQUEST_ALL_KEYS.none { mockSpec.containsKey(it) })
        throw ContractException(errorMessage = "Stub does not contain http-request/mock-http-request as a top level key.")
    if (MOCK_HTTP_RESPONSE_ALL_KEYS.none { mockSpec.containsKey(it) })
        throw ContractException(errorMessage = "Stub does not contain http-request/mock-http-request as a top level key.")
}

fun mockFromJSON(mockSpec: Map<String, Value>): ScenarioStub {
    val data = JSONObjectValue(mockSpec.filterKeys { it !in (MOCK_HTTP_REQUEST_ALL_KEYS + MOCK_HTTP_RESPONSE_ALL_KEYS).plus(PARTIAL) })

    if(PARTIAL in mockSpec) {
        val template = mockSpec.getValue(PARTIAL) as? JSONObjectValue ?: throw ContractException("template key must be an object")

        return ScenarioStub(partial = mockFromJSON(template.jsonObject), data = data)
    }

    val mockRequest: HttpRequest = requestFromJSON(getJSONObjectValue(MOCK_HTTP_REQUEST_ALL_KEYS, mockSpec))
    val mockResponse: HttpResponse = HttpResponse.fromJSON(getJSONObjectValue(MOCK_HTTP_RESPONSE_ALL_KEYS, mockSpec))

    val delayInSeconds: Int? = getIntOrNull(DELAY_IN_SECONDS, mockSpec)
    val delayInMilliseconds: Long? = getLongOrNull(DELAY_IN_MILLISECONDS, mockSpec)
    val delayInMs: Long? = delayInMilliseconds ?: delayInSeconds?.let { it.toLong().times(1000) }

    val stubToken: String? = getStringOrNull(TRANSIENT_MOCK_ID, mockSpec)
    val requestBodyRegex: String? = getRequestBodyRegexOrNull(mockSpec)

    return ScenarioStub(
        request = mockRequest,
        response = mockResponse,
        delayInMilliseconds = delayInMs,
        stubToken = stubToken,
        requestBodyRegex = requestBodyRegex,
        data = data
    )
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

