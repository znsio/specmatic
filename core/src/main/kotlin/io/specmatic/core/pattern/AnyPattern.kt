package io.specmatic.core.pattern

import io.specmatic.core.*
import io.specmatic.core.pattern.config.NegativePatternConfiguration
import io.specmatic.core.value.EmptyString
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.ScalarValue
import io.specmatic.core.value.Value

data class AnyPattern(
    override val pattern: List<Pattern>,
    val key: String? = null,
    override val typeAlias: String? = null,
    override val example: String? = null
) : Pattern, HasDefaultExample {
    override fun equals(other: Any?): Boolean = other is AnyPattern && other.pattern == this.pattern

    override fun hashCode(): Int = pattern.hashCode()

    data class AnyPatternMatch(val pattern: Pattern, val result: Result)

    override fun fillInTheBlanks(value: Value, dictionary: Map<String, Value>, resolver: Resolver): ReturnValue<Value> {
        val results = pattern.asSequence().map {
            it.fillInTheBlanks(value, dictionary, resolver)
        }

        val successfulGeneration = results.firstOrNull { it is HasValue }

        if(successfulGeneration != null)
            return successfulGeneration

        val failures = results.toList().filterIsInstance<Result.Failure>()

        return HasFailure(Result.Failure.fromFailures(failures))
    }

    override fun resolveSubstitutions(
        substitution: Substitution,
        value: Value,
        resolver: Resolver,
        key: String?
    ): ReturnValue<Value> {
        val options = pattern.map {
            try {
                it.resolveSubstitutions(substitution, value, resolver, key)
            } catch(e: Throwable) {
                HasException(e)
            }
        }

        val hasValue = options.find { it is HasValue }

        if(hasValue != null)
            return hasValue

        val failures = options.map {
            it.realise(
                hasValue = { _, _ ->
                    throw NotImplementedError()
                },
                orFailure = { failure -> failure.failure },
                orException = { exception -> exception.toHasFailure().failure }
            )
        }

        return HasFailure<Value>(Result.Failure.fromFailures(failures))
    }

    override fun getTemplateTypes(key: String, value: Value, resolver: Resolver): ReturnValue<Map<String, Pattern>> {
        val initialValue: ReturnValue<Map<String, Pattern>> = HasValue(emptyMap<String, Pattern>())

        return pattern.fold(initialValue) { acc, pattern ->
            val templateTypes = pattern.getTemplateTypes("", value, resolver)
            acc.assimilate(templateTypes) { data, additional -> data + additional }
        }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        val matchResults = pattern.map {
            AnyPatternMatch(it, resolver.matchesPattern(key, it, sampleData ?: EmptyString))
        }

        val matchResult = matchResults.find { it.result is Result.Success }

        if(matchResult != null)
            return matchResult.result

        val resolvedPatterns = pattern.map { resolvedHop(it, resolver) }

        if(resolvedPatterns.any { it is NullPattern } || resolvedPatterns.all { it is ExactValuePattern })
            return failedToFindAny(
                    typeName,
                    sampleData,
                    getResult(matchResults.map { it.result as Result.Failure }),
                    resolver.mismatchMessages
                )

        val failuresWithUpdatedBreadcrumbs = matchResults.map {
            Pair(it.pattern, it.result as Result.Failure)
        }.mapIndexed { index, (pattern, failure) ->
            val ordinal = index + 1

            pattern.typeAlias?.let {
                if(it.isBlank() || it == "()")
                    failure.breadCrumb("(~~~object $ordinal)")
                else
                    failure.breadCrumb("(~~~${withoutPatternDelimiters(it)} object)")
            } ?:
            failure
        }

        return Result.fromFailures(failuresWithUpdatedBreadcrumbs)
    }

    private fun getResult(failures: List<Result.Failure>): List<Result.Failure> = when {
        isNullablePattern() -> {
            val index = pattern.indexOfFirst { !isEmpty(it) }
            listOf(failures[index])
        }
        else -> failures
    }

    private fun isNullablePattern() = pattern.size == 2 && pattern.any { isEmpty(it) }

    private fun isEmpty(it: Pattern) = it.typeAlias == "(empty)" || it is NullPattern

    override fun generate(resolver: Resolver): Value {
        return resolver.resolveExample(example, pattern) ?: generateRandomValue(resolver)
    }

    private fun generateRandomValue(resolver: Resolver): Value {
        val randomPattern = pattern.random()
        val isNullable = pattern.any { it is NullPattern }
        return resolver.withCyclePrevention(randomPattern, isNullable) { cyclePreventedResolver ->
            when (key) {
                null -> randomPattern.generate(cyclePreventedResolver)
                else -> cyclePreventedResolver.generate(key, randomPattern)
            }
        } ?: NullValue // Terminates cycle gracefully. Only happens if isNullable=true so that it is contract-valid.
    }


    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        resolver.resolveExample(example, pattern)?.let {
            return sequenceOf(HasValue(ExactValuePattern(it)))
        }

        val isNullable = pattern.any { it is NullPattern }
        val patternResults: Sequence<Pair<Sequence<ReturnValue<Pattern>>?, Throwable?>> =
            pattern.asSequence().sortedBy { it is NullPattern }.map { innerPattern ->
                try {
                    val patterns =
                        resolver.withCyclePrevention(innerPattern, isNullable) { cyclePreventedResolver ->
                            innerPattern.newBasedOn(row, cyclePreventedResolver).map { it.value }
                        } ?: sequenceOf()
                    Pair(patterns.map { HasValue(it) }, null)
                } catch (e: Throwable) {
                    Pair(null, e)
                }
            }

        return newTypesOrExceptionIfNone(patternResults, "Could not generate new tests")
    }


    private fun newTypesOrExceptionIfNone(patternResults: Sequence<Pair<Sequence<ReturnValue<Pattern>>?, Throwable?>>, message: String): Sequence<ReturnValue<Pattern>> {
        val newPatterns: Sequence<ReturnValue<Pattern>> = patternResults.mapNotNull { it.first }.flatten()

        if (!newPatterns.any() && pattern.isNotEmpty()) {
            val exceptions = patternResults.mapNotNull { it.second }.map {
                when (it) {
                    is ContractException -> it
                    else -> ContractException(exceptionCause = it)
                }
            }

            val failures = exceptions.map { it.failure() }

            val failure = Result.Failure.fromFailures(failures.toList())

            throw ContractException(failure.toFailureReport(message))
        }
        return newPatterns
    }

    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> {
        val isNullable = pattern.any {it is NullPattern}
        return pattern.asSequence().flatMap { innerPattern ->
            resolver.withCyclePrevention(innerPattern, isNullable) { cyclePreventedResolver ->
                innerPattern.newBasedOn(cyclePreventedResolver)
            }?: emptySequence()  // Terminates cycle gracefully. Only happens if isNullable=true so that it is contract-valid.
        }
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver, config: NegativePatternConfiguration): Sequence<ReturnValue<Pattern>> {
        val nullable = pattern.any { it is NullPattern }

        val negativeTypeResults = pattern.asSequence().map {
            try {
                val patterns: Sequence<ReturnValue<Pattern>> =
                    it.negativeBasedOn(row, resolver)
                Pair(patterns, null)
            } catch(e: Throwable) {
                Pair(null, e)
            }
        }

        val negativeTypes = newTypesOrExceptionIfNone(
            negativeTypeResults,
            "Could not get negative tests"
        ).let { patterns: Sequence<ReturnValue<Pattern>> ->
            if (nullable)
                patterns.filterValueIsNot { it is NullPattern }
            else
                patterns
        }

        return negativeTypes.distinctBy {
            it.withDefault(randomString(10)) {
                distinctableValueOnlyForScalars(it)
            }
        }
    }

    private fun distinctableValueOnlyForScalars(it: Pattern): Any {
        if (it is ScalarType || it is ExactValuePattern)
            return it

        return randomString(10)
    }

    override fun parse(value: String, resolver: Resolver): Value {
        val resolvedTypes = pattern.map { resolvedHop(it, resolver) }
        val nonNullTypesFirst = resolvedTypes.filterNot { it is NullPattern }.plus(resolvedTypes.filterIsInstance<NullPattern>())

        return nonNullTypesFirst.asSequence().map {
            try {
                it.parse(value, resolver)
            } catch (e: Throwable) {
                null
            }
        }.find { it != null } ?: throw ContractException(
            "Failed to parse value \"$value\". It should have matched one of ${
                pattern.joinToString(
                    ", "
                ) { it.typeName }
            }."
        )
    }

    override fun patternSet(resolver: Resolver): List<Pattern> =
        this.pattern.flatMap { it.patternSet(resolver) }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        val compatibleResult = otherPattern.fitsWithin(patternSet(thisResolver), otherResolver, thisResolver, typeStack)

        return if(compatibleResult is Result.Failure && allValuesAreScalar())
            mismatchResult(this, otherPattern, thisResolver.mismatchMessages)
        else
            compatibleResult
    }

    private fun allValuesAreScalar() = pattern.all { it is ExactValuePattern && it.pattern is ScalarValue }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        if (pattern.isEmpty())
            throw ContractException("AnyPattern doesn't have any types, so can't infer which type of list to wrap the given value in")

        return pattern.first().listOf(valueList, resolver)
    }

    override val typeName: String
        get() {
            return if (pattern.size == 2 && isNullablePattern()) {
                val concreteTypeName =
                    withoutPatternDelimiters(pattern.filterNot { it is NullPattern || it.typeAlias == "(empty)" }
                        .first().typeName)
                "($concreteTypeName?)"
            } else
                "(${pattern.joinToString(" or ") { inner -> withoutPatternDelimiters(inner.typeName).let { if(it == "null") "\"null\"" else it}  }})"
        }

    override fun toNullable(defaultValue: String?): Pattern {
        return this
    }
}

private fun failedToFindAny(expected: String, actual: Value?, results: List<Result.Failure>, mismatchMessages: MismatchMessages): Result.Failure =
    when (results.size) {
        1 -> results[0]
        else -> {
            mismatchResult(expected, actual, mismatchMessages)
        }
    }
