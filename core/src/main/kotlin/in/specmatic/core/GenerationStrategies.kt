package `in`.specmatic.core

import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.pattern.isOptional
import `in`.specmatic.core.value.Value

interface GenerationStrategies {
    val negativePrefix: String
    val positivePrefix: String

    fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): Sequence<Pattern>
    fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): Sequence<Pattern>
    fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row): Sequence<Pattern>
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

data class GenerativeTestsEnabled(private val positiveOnly: Boolean = Flags.onlyPositive()) : GenerationStrategies {
    override val negativePrefix: String = "-ve "
    override val positivePrefix: String = "+ve "

    override fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): Sequence<Pattern> {
        // TODO generate value outside
        return resolver.withCyclePrevention(pattern, isOptional(key)) { cyclePreventedResolver ->
            pattern.newBasedOn(Row(), cyclePreventedResolver)
        } ?: emptySequence()
    }

    override fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): Sequence<Pattern> {
        // TODO generate value outside
        val requestsFromFlattenedRow: Sequence<Pattern> =
            resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                body.newBasedOn(row.noteRequestBody(), cyclePreventedResolver)
            }

        var matchFound = false

        val iterator = requestsFromFlattenedRow.iterator()

        return sequence {

            while(iterator.hasNext()) {
                val next = iterator.next()

                if(next.encompasses(requestBodyAsIs, resolver, resolver, emptySet()) is Success)
                    matchFound = true

                yield(next)
            }

            if(!matchFound)
                yield(requestBodyAsIs)
        }
    }

    override fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row): Sequence<Pattern> {
        // TODO generate value outside
        val vanilla = resolver.withCyclePrevention(body) { cyclePreventedResolver ->
            body.newBasedOn(Row(), cyclePreventedResolver)
        }
        val fromExamples = resolver.withCyclePrevention(body) { cyclePreventedResolver ->
            body.newBasedOn(row, cyclePreventedResolver)
        }
        val remainingVanilla = vanilla.filterNot { vanillaType ->
            fromExamples.any { typeFromExamples ->
                vanillaType.encompasses(
                    typeFromExamples,
                    resolver,
                    resolver
                ).isSuccess()
            }
        }

        return fromExamples.plus(remainingVanilla)
    }

    override fun resolveRow(row: Row): Row {
        return Row()
    }

    override fun generateKeySubLists(key: String, subList: List<String>): Sequence<List<String>> {
        return if(isOptional(key)) {
            sequenceOf(subList, subList + key)
        } else
            sequenceOf(subList + key)
    }

    override fun positiveTestScenarios(feature: Feature, suggestions: List<Scenario>): Sequence<Pair<Scenario, ReturnValue<Scenario>>> {
        return feature.positiveTestScenarios(suggestions)
    }

    override fun negativeTestScenarios(feature: Feature): Sequence<Scenario> {
        return if(positiveOnly)
            emptySequence()
        else
            feature.negativeTestScenarios()
    }

    override fun fillInTheMissingMapPatterns(
        newQueryParamsList: Sequence<Map<String, Pattern>>,
        queryPatterns: Map<String, Pattern>,
        additionalProperties: Pattern?,
        row: Row,
        resolver: Resolver
    ): Sequence<Map<String, Pattern>> {
        val additionalPatterns = attempt(breadCrumb = QUERY_PARAMS_BREADCRUMB) {
            val queryParams = queryPatterns.let {
                if(additionalProperties != null)
                    it.plus(randomString(5) to additionalProperties)
                else
                    it
            }

            forEachKeyCombinationIn(queryParams, Row()) { entry ->
                newBasedOn(entry, row, resolver)
            }.map {
                it.mapKeys { withoutOptionality(it.key) }
            }
        }

        return additionalPatterns.map {
            noOverlapBetween(it, newQueryParamsList, resolver)
        }.filterNotNull()
    }
}

object NonGenerativeTests : GenerationStrategies {
    override val negativePrefix: String = ""
    override val positivePrefix: String = ""

    override fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): Sequence<Pattern> {
        return sequenceOf()
    }

    override fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): Sequence<Pattern> {
        return sequenceOf(ExactValuePattern(value))
    }

    override fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row): Sequence<Pattern> {
        return resolver.withCyclePrevention(body) { cyclePreventedResolver ->
            body.newBasedOn(row, cyclePreventedResolver)
        }
    }

    override fun resolveRow(row: Row): Row {
        return row
    }

    override fun generateKeySubLists(key: String, subList: List<String>): Sequence<List<String>> {
        return sequenceOf(subList + key)
    }

    override fun positiveTestScenarios(feature: Feature, suggestions: List<Scenario>): Sequence<Pair<Scenario, ReturnValue<Scenario>>> {
        return feature.positiveTestScenarios(suggestions)
    }

    override fun negativeTestScenarios(feature: Feature): Sequence<Scenario> {
        return sequenceOf()
    }

    override fun fillInTheMissingMapPatterns(
        newQueryParamsList: Sequence<Map<String, Pattern>>,
        queryPatterns: Map<String, Pattern>,
        additionalProperties: Pattern?,
        row: Row,
        resolver: Resolver
    ): Sequence<Map<String, Pattern>> {
        return emptySequence()
    }
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