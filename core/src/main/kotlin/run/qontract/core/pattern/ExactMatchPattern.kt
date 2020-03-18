package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.OriginalValue
import run.qontract.core.value.Value

data class ExactMatchPattern(override val pattern: Any) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (pattern == sampleData?.value) {
            true -> Result.Success()
            else -> Result.Failure("Expected: $pattern Actual: $sampleData")
        }
    }

    override fun generate(resolver: Resolver) = OriginalValue(pattern)
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
}