package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.Value
import run.qontract.core.withNumericStringPattern

data class ListPattern(override val pattern: Pattern) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONArrayValue)
            return mismatchResult("JSON array", sampleData)

        return sampleData.list.asSequence().map {
            pattern.matches(it, withNumericStringPattern(resolver))
        }.mapIndexed { index, result -> Pair(index, result) }.find { it.second is Result.Failure }?.let { (index, result) ->
            when(result) {
                is Result.Failure -> result.breadCrumb("[$index]")
                else -> Result.Success()
            }
        } ?: Result.Success()
    }

    override fun generate(resolver: Resolver): JSONArrayValue =
        JSONArrayValue(0.until(randomNumber(10)).mapIndexed{ index, _ ->
            attempt(breadCrumb = "[$index (random)]") { pattern.generate(resolver) }
        })
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = attempt(breadCrumb = "[]") { pattern.newBasedOn(row, resolver).map { ListPattern(it) } }
    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value)
    override fun matchesPattern(pattern: Pattern, resolver: Resolver): Boolean =
            pattern is ListPattern && pattern.pattern.matchesPattern(this.pattern, resolver)

    override val description: String = "list of ${pattern.description}"
}
