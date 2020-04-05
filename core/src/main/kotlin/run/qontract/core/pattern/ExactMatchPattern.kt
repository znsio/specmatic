package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.NullValue
import run.qontract.core.value.Value

data class ExactMatchPattern(override val pattern: Value) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData is NullValue) {
            return when(pattern) {
                NullValue -> Result.Success()
                else -> Result.Failure("Expected ${pattern.toDisplayValue()} but got null")
            }
        }

        return when (pattern == sampleData) {
            true -> Result.Success()
            else -> Result.Failure("Expected ${pattern.toDisplayValue()}, actual ${sampleData?.toDisplayValue()}")
        }
    }

    override fun generate(resolver: Resolver) = pattern
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value = pattern
}