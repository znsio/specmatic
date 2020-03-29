package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.NumberValue
import run.qontract.core.value.Value
import java.util.*

class NumberTypePattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when(sampleData is NumberValue) {
            true -> Result.Success()
            false -> Result.Failure("${sampleData?.value} is not a Number")
        }
    }

    override fun generate(resolver: Resolver): Value = NumberValue(Random().nextInt(1000))
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value {
        return NumberValue(convertToNumber(value))
    }

    override val pattern: Any = "(number)"
    override fun toString(): String = pattern.toString()
}