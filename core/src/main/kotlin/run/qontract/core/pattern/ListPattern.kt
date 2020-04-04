package run.qontract.core.pattern

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.Value
import run.qontract.core.withNumericStringPattern

data class ListPattern(override val pattern: Pattern) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONArrayValue)
            return Result.Failure("Expected: JSONArrayValue. Actual: ${sampleData?.javaClass ?: "null"}")

        return sampleData.list.asSequence().map {
            pattern.matches(it, withNumericStringPattern(resolver))
        }.find { it is Result.Failure }.let { result ->
            when(result) {
                is Result.Failure -> result.add("Expected multiple values of type $pattern, but one of the values didn't match in ${sampleData.list}")
                else -> Result.Success()
            }
        }
    }

    override fun generate(resolver: Resolver): JSONArrayValue = JSONArrayValue(generateMultipleValues(pattern, resolver))
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = pattern.newBasedOn(row, resolver).map { ListPattern(it) }
    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value)
}
