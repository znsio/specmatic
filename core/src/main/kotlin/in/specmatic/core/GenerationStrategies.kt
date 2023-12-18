package `in`.specmatic.core

import `in`.specmatic.core.Result.*
import `in`.specmatic.core.pattern.ExactValuePattern
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.isOptional
import `in`.specmatic.core.value.Value
import kotlin.Result

interface GenerationStrategies {
    fun generatedPatternsForGenerativeTests(resolver: Resolver, pattern: Pattern, key: String): List<Pattern>
    fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): List<Pattern>
    fun generateHttpRequests(resolver: Resolver, body: Pattern, row: Row): List<Pattern>
    fun resolveRow(resolver: Resolver, row: Row): Row
    fun generateKeySubLists(resolver: Resolver, key: String, subList: List<String>): List<List<String>>
}

class GenerativeTestsEnabled : GenerationStrategies {
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
}

class NonGenerativeTests : GenerationStrategies {
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
}