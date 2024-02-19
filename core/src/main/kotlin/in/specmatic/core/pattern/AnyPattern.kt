package `in`.specmatic.core.pattern

import `in`.specmatic.core.MismatchMessages
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.ScalarValue
import `in`.specmatic.core.value.Value

data class AnyPattern(
    override val pattern: List<Pattern>,
    val key: String? = null,
    override val typeAlias: String? = null,
    override val example: String? = null
) : Pattern, HasDefaultExample {
    override fun equals(other: Any?): Boolean = other is AnyPattern && other.pattern == this.pattern

    override fun hashCode(): Int = pattern.hashCode()

    data class AnyPatternMatch(val pattern: Pattern, val result: Result)

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

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        resolver.resolveExample(example, pattern)?.let {
            return listOf(ExactValuePattern(it))
        }

        val isNullable = pattern.any { it is NullPattern }
        val patternResults: List<Pair<List<Pattern>?, Throwable?>> =
            pattern.sortedBy { it is NullPattern }.map { innerPattern ->
                try {
                    val patterns =
                        resolver.withCyclePrevention(innerPattern, isNullable) { cyclePreventedResolver ->
                            innerPattern.newBasedOn(row, cyclePreventedResolver)
                        } ?: listOf()
                    Pair(patterns, null)
                } catch (e: Throwable) {
                    Pair(null, e)
                }
            }

        return newTypesOrExceptionIfNone(patternResults, "Could not generate new tests")
    }

    private fun newTypesOrExceptionIfNone(patternResults: List<Pair<List<Pattern>?, Throwable?>>, message: String): List<Pattern> {
        val newPatterns: List<Pattern> = patternResults.mapNotNull { it.first }.flatten()

        if (newPatterns.isEmpty() && pattern.isNotEmpty()) {
            val exceptions = patternResults.mapNotNull { it.second }.map {
                when (it) {
                    is ContractException -> it
                    else -> ContractException(exceptionCause = it)
                }
            }

            val failures = exceptions.map { it.failure() }

            val failure = Result.Failure.fromFailures(failures)

            throw ContractException(failure.toFailureReport(message))
        }
        return newPatterns
    }

    override fun newBasedOn(resolver: Resolver): List<Pattern> {
        val isNullable = pattern.any {it is NullPattern}
        return pattern.flatMap { innerPattern ->
            resolver.withCyclePrevention(innerPattern, isNullable) { cyclePreventedResolver ->
                innerPattern.newBasedOn(cyclePreventedResolver)
            }?: listOf()  // Terminates cycle gracefully. Only happens if isNullable=true so that it is contract-valid.
        }
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        val nullable = pattern.any { it is NullPattern }

        val negativeTypeResults = pattern.map {
            try {
                val patterns =
                    it.negativeBasedOn(row, resolver)
                Pair(patterns, null)
            } catch(e: Throwable) {
                Pair(null, e)
            }
        }

        val negativeTypes = newTypesOrExceptionIfNone(
            negativeTypeResults,
            "Could not get negative tests"
        ).let { patterns ->
            if (nullable)
                patterns.filterNot { it is NullPattern }
            else
                patterns
        }

        return if(negativeTypes.all { it is ScalarType })
            negativeTypes.distinct()
        else
            negativeTypes
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
