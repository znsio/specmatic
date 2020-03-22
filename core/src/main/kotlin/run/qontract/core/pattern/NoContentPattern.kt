package run.qontract.core.pattern

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.NoValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

class NoContentPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (sampleData) {
            is StringValue -> when (sampleData.string.isEmpty()) {
                true -> Result.Success()
                else -> Result.Failure("Expected content to be empty. However it was $sampleData.")
            }
            is NoValue -> Result.Success()
            null -> Result.Success()
            else -> Result.Failure("${sampleData.value} is not empty.")
        }
    }

    override fun generate(resolver: Resolver): Value = StringValue("")
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value {
        return when {
            value.isEmpty() -> NoValue()
            else -> throw ContractParseException("""Parsing to $javaClass, but "$value" is not empty""")
        }
    }

    override val pattern: Any = ""

    override fun toString(): String = "(Nothing)"
}