package `in`.specmatic.core.pattern

import `in`.specmatic.core.MismatchMessages
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.ScalarValue
import `in`.specmatic.core.value.Value

data class AnyPattern(
    override val pattern: List<Pattern>,
    val key: String? = null,
    override val typeAlias: String? = null
) : Pattern {
    override fun equals(other: Any?): Boolean = other is AnyPattern && other.pattern == this.pattern

    override fun hashCode(): Int = pattern.hashCode()

    override fun matches(sampleData: Value?, resolver: Resolver): Result =
        pattern.asSequence().map {
            resolver.matchesPattern(key, it, sampleData ?: EmptyString)
        }.let { results ->
            results.find { it is Result.Success } ?: failedToFindAny2(
                typeName,
                sampleData,
                getResult(results.map { it as Result.Failure }.toList()),
                resolver.mismatchMessages
            )
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

    override fun generate(resolver: Resolver): Value =
        when (key) {
            null -> pattern.random().generate(resolver)
            else -> resolver.generate(key, pattern.random())
        }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> =
        pattern.flatMap { it.newBasedOn(row, resolver) }

    override fun newBasedOn(resolver: Resolver): List<Pattern> =
        pattern.flatMap { it.newBasedOn(resolver) }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(NullPattern)

    override fun parse(value: String, resolver: Resolver): Value =
        pattern.asSequence().map {
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

        return pattern.single().listOf(valueList, resolver)
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
}

private fun failedToFindAny(description: String, results: List<Result.Failure>): Result.Failure =
    when (results.size) {
        1 -> results[0]
        else -> {
            val actual = results.first().message.replace(Regex("Expected.*actual was "), "")
            Result.Failure("""Expected $description, Actual was $actual""".trim())
        }
    }

private fun failedToFindAny2(expected: String, actual: Value?, results: List<Result.Failure>, mismatchMessages: MismatchMessages): Result.Failure =
    when (results.size) {
        1 -> results[0]
        else -> {
            mismatchResult(expected, actual, mismatchMessages)
        }
    }
