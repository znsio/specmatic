package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.Value

object NonGenerativeTests : GenerationStrategies {
    override fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): Sequence<Pattern> {
        return sequenceOf()
    }

    override fun generateHttpRequestBodies(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): Sequence<Pattern> {
        return sequenceOf(ExactValuePattern(value))
    }

    override fun generateHttpRequestBodies(resolver: Resolver, body: Pattern, row: Row): Sequence<Pattern> {
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

    override fun negativeTestScenarios(feature: Feature): Sequence<Pair<Scenario, ReturnValue<Scenario>>> {
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