package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.BooleanValue
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.util.*

class BooleanPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result =
        when(sampleData) {
            is BooleanValue -> Result.Success()
            is StringValue -> matches(parsedValue(sampleData.string), resolver)
            else -> mismatchResult("boolean", sampleData)
        }

    override fun generate(resolver: Resolver): Value =
        when(Random().nextInt(1)) {
            0 -> BooleanValue(false)
            else -> BooleanValue(true)
        }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value = BooleanValue(value.toBoolean())

    override val pattern: Any = "(boolean)"
    override fun toString(): String = pattern.toString()
}