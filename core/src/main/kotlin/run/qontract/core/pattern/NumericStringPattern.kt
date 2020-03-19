package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.util.*

class NumericStringPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result = when(sampleData) {
        is NumberValue -> Result.Success()
        !is StringValue -> Result.Failure(""""$sampleData" is not a Numeric String""")
        else -> if(isNumber(sampleData)) Result.Success() else Result.Failure(""""${sampleData.string}" is not a Number""")
    }

    private fun isNumber(value: StringValue) = isInt(value.string) || isFloat(value.string) || isDouble(value.string)
    private fun isInt(value: String) = try { value.toInt().run { true } } catch(e: Exception) { false }
    private fun isFloat(value: String) = try { value.toFloat().run { true } } catch(e: Exception) { false }
    private fun isDouble(value: String) = try { value.toDouble().run { true } } catch(e: Exception) { false }

    override fun generate(resolver: Resolver): Value = NumberValue(Random().nextInt(1000))

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)

    override val pattern: Any = "(number)"
}