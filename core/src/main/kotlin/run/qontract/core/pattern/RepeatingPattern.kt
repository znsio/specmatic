package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.Value

data class RepeatingPattern(val patternSpec: String) : Pattern {
    private val cleanPatternSpec = extractPatternFromRepeatingToken(patternSpec)

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONArrayValue)
            return Result.Failure("Expected: JSONArrayValue. Actual: ${sampleData?.javaClass ?: "null"}")

        val resolverWithNumberType = resolver.copy().also {
            it.addCustomPattern("(number)", NumberTypePattern())
        }

        return sampleData.list.map {
            resolverWithNumberType.matchesPattern(null, cleanPatternSpec, it ?: "")
        }.find { it is Result.Failure }.let { result ->
            when(result) {
                is Result.Failure -> result.add("Expected: $patternSpec. But one of the values didn't match in ${sampleData.list}")
                else -> Result.Success()
            }
        }
    }

    override fun generate(resolver: Resolver): Value = JSONArrayValue(generateMultipleValues(cleanPatternSpec, resolver))

    override fun newBasedOn(row: Row, resolver: Resolver): Pattern =
        JSONArrayPattern(1.until(randomNumber(10)).map { resolver.getPattern(cleanPatternSpec).newBasedOn(row, resolver).pattern })

    override val pattern: Any = patternSpec
}
