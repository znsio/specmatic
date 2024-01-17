package `in`.specmatic.core

import kotlin.collections.Map

data class QueryParameters(val map: Map<String, String> = kotlin.collections.HashMap(), val paramPairs: List<Pair<String, String>> = map.toList()) : Map<String, String> by map {
    fun plus(map: Map<String, String>): QueryParameters {
        return QueryParameters(this.map + map, paramPairs + map.toList())
    }

    fun plus(pair: Pair<String, String>): QueryParameters {
        return QueryParameters(this.map + pair, paramPairs + pair)
    }

    fun minus(name: String): QueryParameters {
        return QueryParameters(this.map - name, paramPairs.filterNot { it.first == name })
    }
}