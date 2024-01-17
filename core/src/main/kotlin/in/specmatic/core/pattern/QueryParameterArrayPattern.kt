package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value

class QueryParameterArrayPattern(override val pattern: Pattern): Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        val result = try {
            pattern.parse(sampleData.toString(), resolver)
            Result.Success()
        } catch(e: Throwable) {
            Result.Failure("Element $sampleData of the query parameter array did not match the type ${pattern.typeName}. ${exceptionCauseMessage(e)}")
        }
        return result
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
        return StringValue(value)
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        TODO("Not yet implemented")
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String?
        get() = null

    override val typeName: String
        get() = "(queryParameterArray/${pattern.typeName})"
}