package `in`.specmatic.core

import `in`.specmatic.core.pattern.parsedJSONArray
import kotlin.collections.Map

data class QueryParameters(val map: Map<String, String> = kotlin.collections.HashMap(), val paramPairs: List<Pair<String, String>> = mapToListOfPairs(map)) : Map<String, String> by map {
    fun plus(map: Map<String, String>): QueryParameters {
        val newListOfPairs = mapToListOfPairs(map)
        return QueryParameters(this.map + map, paramPairs + newListOfPairs)
    }

    fun plus(pair: Pair<String, String>): QueryParameters {
        val newListOfPairs = pairToListOfPairs(pair)
        return QueryParameters(this.map + pair, paramPairs + newListOfPairs)
    }

    fun minus(name: String): QueryParameters {
        return QueryParameters(this.map - name, paramPairs.filterNot { it.first == name })
    }

    fun asMap():Map<String, String> {
        return paramPairs.toMap()
    }

    fun getValues(key: String): List<String> {
        return paramPairs.filter { it.first == key }.map { it.second }
    }

    override fun containsKey(key: String): Boolean {
        return paramPairs.any { it.first == key }
    }
}

fun mapToListOfPairs(inputMap: Map<String, String>): List<Pair<String, String>> {
    return inputMap.flatMap { (key, value) ->
        toListOfPairs(value, key)
    }
}

fun pairToListOfPairs(pair: Pair<String, String>): List<Pair<String, String>> {
    val key = pair.first
    val value = pair.second
    return toListOfPairs(value, key)
}

private fun toListOfPairs(
    value: String,
    key: String
): List<Pair<String, String>> {
    return if (isJsonArrayString(value)) {
        convertJsonArrayStringToListOfPairs(value, key)
    } else {
        listOf(key to value)
    }
}

private fun convertJsonArrayStringToListOfPairs(
    value: String,
    key: String
) = parsedJSONArray(value)
    .list
    .map { valueItem ->
        key to valueItem.toString().trim()
    }

private fun isJsonArrayString(value: String) = value.startsWith("[") && value.endsWith("]")
