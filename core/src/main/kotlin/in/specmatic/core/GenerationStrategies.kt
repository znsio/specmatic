package `in`.specmatic.core

import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.ExactValuePattern
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.Row
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
    fun positiveTestScenarios(feature: Feature, suggestions: List<Scenario>): Sequence<Scenario>
    fun negativeTestScenarios(feature: Feature): Sequence<Scenario>
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

        return if(requestsFromFlattenedRow.none { p -> p.encompasses(requestBodyAsIs, resolver, resolver, emptySet()) is Success }) {
            requestsFromFlattenedRow.plus(listOf(requestBodyAsIs))
        } else {
            requestsFromFlattenedRow
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

    override fun positiveTestScenarios(feature: Feature, suggestions: List<Scenario>): Sequence<Scenario> {
        return feature.positiveTestScenarios(suggestions)
    }

    override fun negativeTestScenarios(feature: Feature): Sequence<Scenario> {
        return if(positiveOnly)
            emptySequence()
        else
            feature.negativeTestScenarios()
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

    override fun positiveTestScenarios(feature: Feature, suggestions: List<Scenario>): Sequence<Scenario> {
        return feature.positiveTestScenarios(suggestions)
    }

    override fun negativeTestScenarios(feature: Feature): Sequence<Scenario> {
        return sequenceOf()
    }
}