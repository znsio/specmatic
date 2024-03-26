package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value

interface Pattern {
    fun matches(sampleData: Value?, resolver: Resolver): Result
    fun matches(sampleData: List<Value>, resolver: Resolver): ConsumeResult<Value, Value> {
        val sample = sampleData.firstOrNull() ?: return ConsumeResult(Result.Failure("No data found. There should have been at least one."), emptyList())

        val result = this.matches(sample, resolver)

        return ConsumeResult(result, sampleData.drop(1))
    }

    fun generate(resolver: Resolver): Value
    fun generateWithAll(resolver: Resolver) = resolver.withCyclePrevention(this, this::generate)
    fun newBasedOn(row: Row, resolver: Resolver): Sequence<Pattern>
    fun newBasedOnR(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        return try {
            newBasedOn(row, resolver).map { HasValue(it) }
        } catch(e: Throwable) {
            sequenceOf(HasException(e))
        }
    }

    fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<Pattern>
    fun newBasedOn(resolver: Resolver): Sequence<Pattern>
    fun parse(value: String, resolver: Resolver): Value

    fun patternSet(resolver: Resolver): List<Pattern> = listOf(this)

    fun parseToType(valueString: String, resolver: Resolver): Pattern {
        return parse(valueString, resolver).exactMatchElseType()
    }

    fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack = emptySet()): Result
    fun encompasses(others: List<Pattern>, thisResolver: Resolver, otherResolver: Resolver, lengthError: String, typeStack: TypeStack = emptySet()): ConsumeResult<Pattern, Pattern> {
        val otherOne = others.firstOrNull()
                ?: return ConsumeResult(Result.Failure(lengthError), emptyList())

        val result = when {
            otherOne is ExactValuePattern && otherOne.pattern is StringValue -> ExactValuePattern(this.parse(otherOne.pattern.string, thisResolver))
            else -> otherOne
        }.let { otherOneAdjustedForExactValue -> this.encompasses(otherOneAdjustedForExactValue, thisResolver, otherResolver, typeStack) }

        return ConsumeResult(result, others.drop(1))
    }

    fun fitsWithin(otherPatterns: List<Pattern>, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val myPatternSet = patternSet(thisResolver)

        val result = myPatternSet.map { myPattern ->
            val encompassResult = otherPatterns.asSequence().map { otherPattern ->
                biggerEncompassesSmaller(otherPattern, myPattern, thisResolver, otherResolver, typeStack)
            }

            encompassResult.find { it is Result.Success } ?: encompassResult.first()
        }

        return result.find { it is Result.Failure } ?: Result.Success()
    }

    fun listOf(valueList: List<Value>, resolver: Resolver): Value

    fun toNullable(defaultValue: String?): Pattern {
        return AnyPattern(listOf(NullPattern, this), example = defaultValue)
    }

    val typeAlias: String?
    val typeName: String
    val pattern: Any
}
