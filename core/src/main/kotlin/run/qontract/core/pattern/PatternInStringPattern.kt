package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

data class PatternInStringPattern(override val pattern: Pattern = StringPattern, override val typeAlias: String? = null): Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is StringValue)
            return mismatchResult(pattern, sampleData)

        val value = try {
            pattern.parse(sampleData.string, resolver)
        } catch(e: Throwable) {
            return Result.Failure("Could not parse ${sampleData.displayableValue()} to ${pattern.typeName}")
        }

        return pattern.matches(value, resolver)
    }

    override fun generate(resolver: Resolver): Value = StringValue(pattern.generate(resolver).toStringValue())

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> =
            pattern.newBasedOn(row, resolver).map { PatternInStringPattern(it) }

    override fun parse(value: String, resolver: Resolver): Value = StringValue(pattern.parse(value, resolver).toStringValue())

    override fun patternSet(resolver: Resolver): List<Pattern> =
            pattern.patternSet(resolver)

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result =
            when (otherPattern) {
                is ExactValuePattern -> matches(otherPattern.pattern, thisResolver)
                !is PatternInStringPattern -> Result.Failure("Expected type in string type, got ${otherPattern.typeName}")
                else -> otherPattern.pattern.fitsWithin(patternSet(thisResolver), otherResolver, thisResolver, typeStack)
            }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return pattern.listOf(valueList, resolver)
    }

    override val typeName: String = "${pattern.typeName} in string"
}
