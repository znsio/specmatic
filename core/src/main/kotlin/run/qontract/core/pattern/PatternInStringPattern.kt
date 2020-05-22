package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

data class PatternInStringPattern(override val pattern: Pattern = StringPattern): Pattern {
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

    override fun encompasses(otherPattern: Pattern, resolver: Resolver): Boolean =
            otherPattern is PatternInStringPattern
                    && otherPattern.pattern.fitsWithin(pattern.patternSet(resolver), resolver)

    override fun patternSet(resolver: Resolver): List<Pattern> =
            pattern.patternSet(resolver)

    override fun encompasses2(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result =
            when (otherPattern) {
                is ExactValuePattern -> matches(otherPattern.pattern, thisResolver)
                !is PatternInStringPattern -> Result.Failure("Expected type in string type, got ${otherPattern.typeName}")
                else -> otherPattern.pattern.fitsWithin2(patternSet(thisResolver), otherResolver, thisResolver)
            }

    override val typeName: String = "${pattern.typeName} in string"
}
