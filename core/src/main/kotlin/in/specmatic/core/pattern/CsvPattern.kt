package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value

class CsvPattern(override val pattern: Pattern) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is StringValue)
            return resolver.mismatchMessages.valueMismatchFailure("CSV string", sampleData)

        val results: List<Result> = sampleData.string.split(",").mapIndexed { index, value ->
            try {
                pattern.parse(value, resolver)
                Result.Success()
            } catch(e: Throwable) {
                Result.Failure("Element $index did not match the type ${pattern.typeName}. ${exceptionCauseMessage(e)}")
            }
        }

        return Result.fromResults(results)
    }

    override fun generate(resolver: Resolver): Value {
        val max = (2..5).random()

        return StringValue((1..max).map {
            resolver.withCyclePrevention(pattern, pattern::generate)
        }.joinToString(",") { it.toStringLiteral() })
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(this)
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return StringPattern().negativeBasedOn(row, resolver)
    }

    override fun newBasedOn(resolver: Resolver): List<Pattern> {
        return listOf(this)
    }

    override fun parse(value: String, resolver: Resolver): Value {
        return StringValue(value)
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        if(otherPattern !is CsvPattern)
            return Result.Failure(thisResolver.mismatchMessages.mismatchMessage(this.typeName, otherPattern.typeName))

        return this.pattern.encompasses(otherPattern.pattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override fun toString(): String {
        return typeName
    }

    override val typeAlias: String?
        get() = null

    override val typeName: String
        get() = "(csv/${pattern.typeName})"
}