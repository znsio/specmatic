package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.util.*

object NumericStringPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result = when(sampleData) {
        is NumberValue -> Result.Success()
        !is StringValue -> mismatchResult("number", sampleData)
        else -> if(isNumber(sampleData)) Result.Success() else mismatchResult("number", sampleData)
    }

    override fun generate(resolver: Resolver): Value = NumberValue(Random().nextInt(1000))

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value = NumberValue(convertToNumber(value))
    override fun encompasses(otherPattern: Pattern, resolver: Resolver): Boolean = otherPattern is NumericStringPattern
    override fun encompasses2(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver)
    }

    override val typeName: String = "number"

    override fun toString() = pattern.toString()

    override val pattern: Any = "(number)"
}

fun encompasses(thisPattern: Pattern, otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result =
        when {
            otherPattern::class == thisPattern::class -> Result.Success()
            otherPattern is ExactValuePattern -> otherPattern.fitsWithin2(thisPattern.patternSet(thisResolver), otherResolver, thisResolver)
            else -> mismatchResult(thisPattern, otherPattern)
        }
