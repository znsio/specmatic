package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.pattern.isOptional
import `in`.specmatic.core.pattern.withoutOptionality
import `in`.specmatic.core.value.Value

data class GenerativeTestsEnabled(private val positiveOnly: Boolean = Flags.onlyPositive()) : GenerationStrategies {
    override val negativePrefix: String = "-ve "
    override val positivePrefix: String = "+ve "

    override fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): Sequence<Pattern> {
        // TODO generate value outside
        return resolver.withCyclePrevention(pattern, isOptional(key)) { cyclePreventedResolver ->
            pattern.newBasedOn(Row(), cyclePreventedResolver)
        } ?: emptySequence()
    }

    override fun generateHttpRequestBodies(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): Sequence<ReturnValue<Pattern>> {
        // TODO generate value outside
        val requestsFromFlattenedRow: Sequence<ReturnValue<Pattern>> =
            resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                body.newBasedOnR(row.noteRequestBody(), cyclePreventedResolver)
            }

        var matchFound = false

        val iterator = requestsFromFlattenedRow.iterator()

        return sequence {

            while(iterator.hasNext()) {
                val next = iterator.next()

                next.ifValue { _next ->
                    if(_next.encompasses(requestBodyAsIs, resolver, resolver, emptySet()) is Result.Success)
                        matchFound = true
                }

                yield(next)
            }

            if(!matchFound)
                yield(HasValue(requestBodyAsIs))
        }
    }

    override fun generateHttpRequestBodies(resolver: Resolver, body: Pattern, row: Row): Sequence<ReturnValue<Pattern>> {
        // TODO generate value outside
        val vanilla = resolver.withCyclePrevention(body) { cyclePreventedResolver ->
            body.newBasedOnR(Row(), cyclePreventedResolver)
        }
        val fromExamples = resolver.withCyclePrevention(body) { cyclePreventedResolver ->
            body.newBasedOnR(row, cyclePreventedResolver)
        }
        val remainingVanilla = vanilla.filterNot { vanillaType ->
            vanillaType.withDefault(false) { _vanillaType ->
                fromExamples.any { typeFromExamples ->
                    typeFromExamples.withDefault(false) { _typeFromExaxmples ->
                        _vanillaType.encompasses(
                            _typeFromExaxmples,
                            resolver,
                            resolver
                        ).isSuccess()
                    }
                }
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
                if (additionalProperties != null)
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