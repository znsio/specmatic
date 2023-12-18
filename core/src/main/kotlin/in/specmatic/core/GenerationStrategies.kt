package `in`.specmatic.core

import `in`.specmatic.core.Result.*
import `in`.specmatic.core.pattern.ExactValuePattern
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.isOptional
import `in`.specmatic.core.value.Value
import kotlin.Result

interface GenerationStrategies {
    val negativePrefix: String
    val positivePrefix: String

    fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): List<Pattern>
    fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): List<Pattern>
    fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row): List<Pattern>
    fun resolveRow(resolver: Resolver, row: Row): Row
    fun generateKeySubLists(resolver: Resolver, key: String, subList: List<String>): List<List<String>>
    fun positiveTestScenarios(feature: Feature, suggestions: List<Scenario>): List<Scenario>
    fun negativeTestScenarios(feature: Feature, suggestions: List<Scenario>): List<Scenario>
}

class GenerativeTestsEnabled : GenerationStrategies {
    override val negativePrefix: String = "-ve "
    override val positivePrefix: String = "+ve "

    override fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): List<Pattern> {
        // TODO generate value outside
        return resolver.withCyclePrevention(pattern, isOptional(key)) { cyclePreventedResolver ->
            pattern.newBasedOn(Row(), cyclePreventedResolver)
        } ?: emptyList()
    }

    override fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): List<Pattern> {
        // TODO generate value outside
        val requestsFromFlattenedRow: List<Pattern> =
            resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                body.newBasedOn(row.noteRequestBody(), cyclePreventedResolver)
            }

        return if(requestsFromFlattenedRow.none { p -> p.encompasses(requestBodyAsIs, resolver, resolver, emptySet()) is Success }) {
            requestsFromFlattenedRow.plus(listOf(requestBodyAsIs))
        } else {
            requestsFromFlattenedRow
        }
    }

    override fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row): List<Pattern> {
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

    override fun resolveRow(resolver: Resolver, row: Row): Row {
        return Row()
    }

    override fun generateKeySubLists(resolver: Resolver, key: String, subList: List<String>): List<List<String>> {
        return if(isOptional(key)) {
            listOf(subList, subList + key)
        } else
            listOf(subList + key)
    }

    override fun positiveTestScenarios(feature: Feature, suggestions: List<Scenario>): List<Scenario> {
        return feature.positiveTestScenarios(suggestions)
    }

    override fun negativeTestScenarios(feature: Feature, suggestions: List<Scenario>): List<Scenario> {
        return feature.negativeTestScenariosUnlessDisabled()
    }
}

class NonGenerativeTests : GenerationStrategies {
    override val negativePrefix: String = ""
    override val positivePrefix: String = ""

    override fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): List<Pattern> {
        return emptyList()
    }

    override fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): List<Pattern> {
        return listOf(ExactValuePattern(value))
    }

    override fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row): List<Pattern> {
        return resolver.withCyclePrevention(body) { cyclePreventedResolver ->
            body.newBasedOn(row, cyclePreventedResolver)
        }
    }

    override fun resolveRow(resolver: Resolver, row: Row): Row {
        return row
    }

    override fun generateKeySubLists(resolver: Resolver, key: String, subList: List<String>): List<List<String>> {
        return listOf(subList + key)
    }

    override fun positiveTestScenarios(feature: Feature, suggestions: List<Scenario>): List<Scenario> {
        return feature.positiveTestScenarios(suggestions)
    }

    override fun negativeTestScenarios(feature: Feature, suggestions: List<Scenario>): List<Scenario> {
        return emptyList()
    }
}