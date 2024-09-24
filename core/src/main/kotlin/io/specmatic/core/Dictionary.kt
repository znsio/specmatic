package io.specmatic.core

import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

class Dictionary(val map: Map<String, Value> = emptyMap()) {
    private fun substituteDictionaryValues(value: JSONArrayValue, paths: List<String> = emptyList(), forceSubstitution: Boolean = false): Value {
        val newList = value.list.mapIndexed { index, valueInArray ->
            val indexesToAdd = listOf("[$index]", "[*]")

            val updatedPaths = paths.flatMap { path ->
                indexesToAdd.map { indexToAdd ->
                    path + indexToAdd
                }
            }.ifEmpty {
                indexesToAdd
            }

            substituteDictionaryValues(valueInArray, updatedPaths, forceSubstitution)
        }

        return value.copy(list = newList)
    }

    private fun substituteDictionaryValues(value: JSONObjectValue, paths: List<String> = emptyList(), forceSubstitution: Boolean = false): Value {
        val newMap = value.jsonObject.mapValues { (key, value) ->

            val updatedPaths = paths.map { path ->
                path + ".$key"
            }.ifEmpty { listOf(key) }

            val pathFoundInDictionary = updatedPaths.firstOrNull { it in map }
            if(value is StringValue && (isVanillaPatternToken(value.string) || forceSubstitution) && pathFoundInDictionary != null) {
                map.getValue(pathFoundInDictionary)
            } else {
                substituteDictionaryValues(value, updatedPaths, forceSubstitution)
            }
        }

        return value.copy(jsonObject = newMap)
    }

    private fun substituteDictionaryValues(value: Value, paths: List<String> = emptyList(), forceSubstitution: Boolean = false): Value {
        return when (value) {
            is JSONObjectValue -> {
                substituteDictionaryValues(value, paths, forceSubstitution)
            }
            is JSONArrayValue -> {
                substituteDictionaryValues(value, paths, forceSubstitution)
            }
            else -> value
        }
    }

    fun substituteDictionaryValues(httpResponse: HttpResponse, forceSubstitution: Boolean = false): HttpResponse {
        val updatedHeaders = httpResponse.headers.mapValues { (headerName, headerValue) ->
            if((isVanillaPatternToken(headerValue) || forceSubstitution) && headerName in map) {
                map.getValue(headerName).toStringLiteral()
            } else headerValue
        }
        val updatedBody = substituteDictionaryValues(httpResponse.body, forceSubstitution = forceSubstitution)

        return httpResponse.copy(headers = updatedHeaders, body= updatedBody)
    }

    fun substituteDictionaryValues(httpRequest: HttpRequest, forceSubstitution: Boolean = false): HttpRequest {
        val updatedHeaders = httpRequest.headers.mapValues { (headerName, headerValue) ->
            if((isVanillaPatternToken(headerValue) || forceSubstitution) && headerName in map) {
                map.getValue(headerName).toStringLiteral()
            } else headerValue
        }
        val updatedBody = substituteDictionaryValues(httpRequest.body, forceSubstitution = forceSubstitution)

        return httpRequest.copy(headers = updatedHeaders, body= updatedBody)
    }
}