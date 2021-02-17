package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.Value
import java.util.*

object NumberPattern : Pattern, ScalarType {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when(sampleData is NumberValue) {
            true -> Result.Success()
            false -> mismatchResult("number", sampleData)
        }
    }

    override fun generate(resolver: Resolver): Value = NumberValue(randomNumber(1000))

    private fun randomNumber(bound: Int) = Random().nextInt(bound)
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value {
        return NumberValue(convertToNumber(value))
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String? = null

    override val typeName: String = "number"

    override val pattern: Any = "(number)"
    override fun toString(): String = pattern.toString()
}

fun encompasses(thisPattern: Pattern, otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result =
        when {
            otherPattern::class == thisPattern::class -> Result.Success()
            otherPattern is ExactValuePattern -> otherPattern.fitsWithin(thisPattern.patternSet(thisResolver), otherResolver, thisResolver, typeStack)
            else -> mismatchResult(thisPattern, otherPattern)
        }
