package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.*

class QueryParameterArrayPattern(override val pattern: Pattern): Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is ListValue)
            return resolver.mismatchMessages.valueMismatchFailure("Array", sampleData)

        val results: List<Result> = sampleData.list.mapIndexed{ index, sampleDataItem ->
            try {
                val parsedValue = pattern.parse(sampleDataItem.toString(), resolver)
                pattern.matches(parsedValue, resolver)
            } catch(e: Throwable) {
                Result.Failure("Element $index ($sampleDataItem) did not match the array type ${pattern.typeName}. ${exceptionCauseMessage(e)}")
            }
        }

        return Result.fromResults(results)
    }

    override fun generate(resolver: Resolver): Value {
        val max = (2..5).random()

        return JSONArrayValue((1..max).map {
            resolver.withCyclePrevention(pattern, pattern::generate)
        }.map { StringValue(it.toStringLiteral()) })
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(this)
    }

    override fun newBasedOn(resolver: Resolver): List<Pattern> {
        return listOf(this)
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
       return pattern.negativeBasedOn(row, resolver)
    }

    override fun parse(value: String, resolver: Resolver): Value {
        return  parsedJSONArray(value, resolver.mismatchMessages)
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        if(otherPattern !is QueryParameterArrayPattern)
            return Result.Failure(thisResolver.mismatchMessages.mismatchMessage(this.typeName, otherPattern.typeName))

        return this.pattern.encompasses(otherPattern.pattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String?
        get() = null

    override val typeName: String
        get() = "(queryParameterArray/${pattern.typeName})"

    override fun parseToType(valueString: String, resolver: Resolver): Pattern {
        return QueryParameterArrayPattern(pattern.parse(valueString, resolver).type())
    }
}