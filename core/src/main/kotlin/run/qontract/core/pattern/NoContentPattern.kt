package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.EmptyString
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

object NoContentPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (sampleData) {
            is StringValue -> when (sampleData.string.isEmpty()) {
                true -> Result.Success()
                else -> mismatchResult("empty string", sampleData)
            }
            is EmptyString, is NullValue -> Result.Success()
            null -> Result.Success()
            else -> mismatchResult("empty string", sampleData)
        }
    }

    override fun generate(resolver: Resolver): Value = StringValue("")
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value {
        return when {
            value.isEmpty() -> EmptyString
            else -> throw ContractException("""Parsing to $javaClass, but "$value" is not empty""")
        }
    }

    override fun matchesPattern(pattern: Pattern, resolver: Resolver): Boolean {
        return pattern is NoContentPattern
    }

    override val description: String = "nothing"

    override val pattern: Any = ""

    override fun toString(): String = "(nothing)"
}