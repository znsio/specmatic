package io.specmatic.core

import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

class Dictionary(private val map: Map<String, Value> = emptyMap()) {
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

    fun substituteDictionaryValues(value: Value, paths: List<String> = emptyList(), forceSubstitution: Boolean = false): Value {
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

    fun substituteDictionaryValues(value: Map<String, String>, forceSubstitution: Boolean = false): Map<String, String> {
        return value.mapValues { (name, value) ->
            if((isVanillaPatternToken(value) || forceSubstitution) && name in map) {
                map.getValue(name).toStringLiteral()
            } else value
        }
    }

    fun lookup(key: String): Value? {
        return map[key]
    }

    fun contains(key: String): Boolean {
        return key in map
    }
}