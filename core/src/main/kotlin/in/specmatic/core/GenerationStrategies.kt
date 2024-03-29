package `in`.specmatic.core

import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.ReturnValue
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.withoutOptionality
import `in`.specmatic.core.value.Value

interface GenerationStrategies {
    val negativePrefix: String
    val positivePrefix: String

    fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): Sequence<Pattern>
    fun generateHttpRequestBodies(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): Sequence<ReturnValue<Pattern>>
    fun generateHttpRequestBodies(resolver: Resolver, body: Pattern, row: Row): Sequence<ReturnValue<Pattern>>
    fun resolveRow(row: Row): Row
    fun generateKeySubLists(key: String, subList: List<String>): Sequence<List<String>>
    fun positiveTestScenarios(feature: Feature, suggestions: List<Scenario>): Sequence<Pair<Scenario, ReturnValue<Scenario>>>
    fun negativeTestScenarios(feature: Feature): Sequence<Scenario>
    fun fillInTheMissingMapPatterns(
        newQueryParamsList: Sequence<Map<String, Pattern>>,
        queryPatterns: Map<String, Pattern>,
        additionalProperties: Pattern?,
        row: Row,
        resolver: Resolver
    ): Sequence<Map<String, Pattern>>
}

internal fun noOverlapBetween(
    map: Map<String, Pattern>,
    otherMaps: Sequence<Map<String, Pattern>>,
    resolver: Resolver
): Map<String, Pattern>? {
    val otherMapsWithSameKeys = otherMaps.filter {
        it.keys.map(::withoutOptionality) == map.keys.map(::withoutOptionality)
    }.map {
        it.mapKeys { withoutOptionality(it.key) }
    }

    val mapWithoutOptionality = map.mapKeys { withoutOptionality(it.key) }

    val results: Sequence<Result> = otherMapsWithSameKeys.map { otherMap ->
        val valueMatchResults = otherMap.map { (key, otherPattern) ->
            val itemPattern = mapWithoutOptionality.getValue(key)

            itemPattern.encompasses(otherPattern, resolver, resolver)
        }

        Result.fromResults(valueMatchResults)
    }

    if(results.any { it is Result.Success })
        return null

    return map
}