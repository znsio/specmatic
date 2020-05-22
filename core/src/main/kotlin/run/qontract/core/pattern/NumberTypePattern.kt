package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.NumberValue
import run.qontract.core.value.Value
import java.util.*

object NumberTypePattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when(sampleData is NumberValue) {
            true -> Result.Success()
            false -> mismatchResult("number", sampleData)
        }
    }

    override fun generate(resolver: Resolver): Value = NumberValue(Random().nextInt(1000))
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value {
        return NumberValue(convertToNumber(value))
    }

    override fun encompasses(otherPattern: Pattern, resolver: Resolver): Boolean = otherPattern is NumberTypePattern
    override fun encompasses2(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver)
    }

    override val typeName: String = "number"

    override val pattern: Any = "(number)"
    override fun toString(): String = pattern.toString()
}