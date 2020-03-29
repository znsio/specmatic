package run.qontract.core.utilities

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.Row
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

class NullPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result =
        when(sampleData) {
            is NullValue -> Result.Success()
            is StringValue -> when(sampleData.string) {
                "null" -> Result.Success()
                else -> Result.Failure("Expected null, got ${sampleData.value}")
            }
            else -> Result.Failure("Expected null, got ${sampleData?.value}")
        }

    override fun generate(resolver: Resolver): Value = NullValue()
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)

    override fun parse(value: String, resolver: Resolver): Value =
        when(value.trim()) {
            "null" -> NullValue()
            else -> throw ContractParseException("Failed to parse $value: it is not null.")
        }

    override val pattern: Any = "(null)"
}
