package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.Value

data class ExactValuePattern(override val pattern: Value, override val typeAlias: String? = null) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (pattern == sampleData) {
            true -> Result.Success()
            else -> mismatchResult(pattern, sampleData, resolver.mismatchMessages)
        }
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        if(otherPattern !is ExactValuePattern || this.pattern != otherPattern.pattern)
            return Result.Failure("Expected ${this.typeName}, got ${otherPattern.typeName}")

        return Result.Success()
    }

    override fun fitsWithin(otherPatterns: List<Pattern>, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val results = otherPatterns.map { it.matches(pattern, otherResolver) }
        return results.find { it is Result.Success } ?: results.firstOrNull() ?: Result.Failure("No matching patterns.")
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return pattern.listOf(valueList)
    }

    override fun generate(resolver: Resolver) = pattern
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun newBasedOn(resolver: Resolver): List<Pattern> = listOf(this)
    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(NullPattern).plus(pattern.type().negativeBasedOn(Row(), resolver).filterNot { it == NullPattern })
    }

    override fun parse(value: String, resolver: Resolver): Value = pattern.type().parse(value, resolver)

    override val typeName: String = pattern.displayableValue()

    override fun toString(): String = pattern.toStringLiteral()
}
