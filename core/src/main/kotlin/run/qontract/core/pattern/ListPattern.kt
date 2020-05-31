package run.qontract.core.pattern

import run.qontract.core.*
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.Value

data class ListPattern(override val pattern: Pattern) : Pattern, EncompassableList {
    override fun getEncompassableList(count: Int, resolver: Resolver): List<Pattern> {
        val resolvedPattern = resolvedHop(pattern, resolver)
        return 0.until(count).map { resolvedPattern }
    }

    override fun isEndless(): Boolean = true

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONArrayValue)
            return mismatchResult("JSON array", sampleData)

        return sampleData.list.asSequence().map {
            pattern.matches(it, resolver)
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
    override fun parse(value: String, resolver: Resolver): Value = parsedJSONStructure(value)

    override fun patternSet(resolver: Resolver): List<Pattern> = pattern.patternSet(resolver)
    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result =
            when (otherPattern) {
                is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolver, thisResolver)
                is JSONArrayPattern -> {
                    try {
                        val results = otherPattern.getEncompassableList(otherResolver).asSequence().mapIndexed { index, otherPatternEntry ->
                            Pair(index, pattern.encompasses(otherPatternEntry, thisResolver, otherResolver))
                        }

                        results.find { it.second is Result.Failure }?.let { result -> result.second.breadCrumb("[${result.first}]") } ?: Result.Success()
                    } catch (e: ContractException) {
                        Result.Failure(e.report())
                    }
                }
                !is ListPattern -> Result.Failure("Expected array or list type, got ${otherPattern.typeName}")
                else -> otherPattern.fitsWithin(patternSet(thisResolver), otherResolver, thisResolver)
            }

    override val typeName: String = "list of ${pattern.typeName}"
}
