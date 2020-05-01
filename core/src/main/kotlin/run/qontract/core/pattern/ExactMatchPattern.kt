package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.NullValue
import run.qontract.core.value.Value

data class ExactMatchPattern(override val pattern: Value) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (pattern == sampleData) {
            true -> Result.Success()
            else -> mismatchResult(pattern, sampleData)
        }
    }

    override fun generate(resolver: Resolver) = pattern
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value = pattern.type().parse(value, resolver)
    override fun matchesPattern(pattern: Pattern, resolver: Resolver): Boolean =
            pattern is ExactMatchPattern && this.pattern == pattern.pattern

    override val displayName: String = pattern.displayableValue()

    override fun toString(): String = pattern.toStringValue()
}