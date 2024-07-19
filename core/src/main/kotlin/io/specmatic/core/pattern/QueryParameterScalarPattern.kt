package io.specmatic.core.pattern

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.ListValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

data class QueryParameterScalarPattern(override val pattern: Pattern): Pattern by pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData == null){
            return Result.Failure("Null found, expected a scalar value")
        }
        val sampleDataString = when (sampleData) {
            is ListValue -> {
                if (sampleData.list.size > 1) return Result.Failure("Multiple values $sampleData found. Expected a single value")
                sampleData.list.single().toStringLiteral()
            }
            else -> sampleData.toStringLiteral()
        }

        return try {
            val parsedValue = if(isPatternToken(sampleDataString)) {
                StringValue(sampleDataString)
            }
            else {
                pattern.parse(sampleDataString, resolver)
            }
            resolver.matchesPattern(null, pattern, parsedValue)
        } catch (e: Throwable) {
            Result.Failure(exceptionCauseMessage(e))
        }
    }

    override fun generate(resolver: Resolver): Value {
        return pattern.generate(resolver)
    }

    override fun parse(value: String, resolver: Resolver): Value {
        return  pattern.parse(value, resolver)
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        if(otherPattern !is QueryParameterScalarPattern)
            return Result.Failure(thisResolver.mismatchMessages.mismatchMessage(this.typeName, otherPattern.typeName))

        return this.pattern.encompasses(otherPattern.pattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String?
        get() = null

    override val typeName: String
        get() = "(queryParameterScalar/${pattern.typeName})"

    override fun parseToType(valueString: String, resolver: Resolver): Pattern {
        return pattern.parse(valueString, resolver).exactMatchElseType()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is QueryParameterScalarPattern) return false
        return pattern == other.pattern
    }

    override fun hashCode(): Int {
        return pattern.hashCode()
    }
}