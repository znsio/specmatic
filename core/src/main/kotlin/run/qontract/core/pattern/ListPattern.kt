package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.breadCrumb
import run.qontract.core.mismatchResult
import run.qontract.core.value.ListValue
import run.qontract.core.value.Value

data class ListPattern(override val pattern: Pattern, override val typeAlias: String? = null) : Pattern, EncompassableList {

    override fun getEncompassableList(): MemberList = MemberList(emptyList(), pattern)

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is ListValue)
            return when {
                resolvedHop(pattern, resolver) is XMLPattern -> mismatchResult("xml nodes", sampleData)
                else -> mismatchResult("JSON array", sampleData)
            }

        val resolverWithEmptyType = withEmptyType(pattern, resolver)

        return sampleData.list.asSequence().map {
            resolverWithEmptyType.matchesPattern(null, pattern, it)
        }.mapIndexed { index, result -> Pair(index, result) }.find { it.second is Result.Failure }?.let { (index, result) ->
            when(result) {
                is Result.Failure -> result.breadCrumb("[$index]")
                else -> Result.Success()
            }
        } ?: Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        val resolverWithEmptyType = withEmptyType(pattern, resolver)
        return pattern.listOf(0.until(randomNumber(10)).mapIndexed{ index, _ ->
            attempt(breadCrumb = "[$index (random)]") { pattern.generate(resolverWithEmptyType) }
        }, resolverWithEmptyType)
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        val resolverWithEmptyType = withEmptyType(pattern, resolver)
        return attempt(breadCrumb = "[]") { pattern.newBasedOn(row, resolverWithEmptyType).map { ListPattern(it) } }
    }
    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value)

    override fun patternSet(resolver: Resolver): List<Pattern> {
        val resolverWithEmptyType = withEmptyType(pattern, resolver)
        return pattern.patternSet(resolverWithEmptyType)
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val thisResolverWithEmptyType = withEmptyType(pattern, thisResolver)
        val otherResolverWithEmptyType = withEmptyType(pattern, otherResolver)

        return when (otherPattern) {
            is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolverWithEmptyType, thisResolverWithEmptyType, typeStack)
            is ListPattern -> otherPattern.fitsWithin(patternSet(thisResolverWithEmptyType), otherResolverWithEmptyType, thisResolverWithEmptyType, typeStack)
            is EncompassableList -> {
                val results = otherPattern.getEncompassableList().getEncompassables(otherResolverWithEmptyType).asSequence().mapIndexed { index, otherPatternEntry ->
                    Pair(index, biggerEncompassesSmaller(pattern, otherPatternEntry, thisResolverWithEmptyType, otherResolverWithEmptyType, typeStack))
                }

                results.find { it.second is Result.Failure }?.let { result -> result.second.breadCrumb("[${result.first}]") } ?: Result.Success()
            }
            else -> Result.Failure("Expected array or list type, got ${otherPattern.typeName}")
        }
    }

    override fun encompasses(others: List<Pattern>, thisResolver: Resolver, otherResolver: Resolver, lengthError: String, typeStack: TypeStack): ConsumeResult {
        val thisResolverWithEmptyType = withEmptyType(pattern, thisResolver)
        val otherResolverWithEmptyType = withEmptyType(pattern, otherResolver)

        val results = others.asSequence().mapIndexed { index, otherPattern ->
            when (otherPattern) {
                is ExactValuePattern ->
                    otherPattern.fitsWithin(listOf(this.pattern), otherResolverWithEmptyType, thisResolverWithEmptyType, typeStack)
                is ListPattern ->
                    otherPattern.fitsWithin(patternSet(thisResolverWithEmptyType), otherResolverWithEmptyType, thisResolverWithEmptyType, typeStack)
                is EncompassableList ->
                    biggerEncompassesSmaller(pattern, resolvedHop(otherPattern, otherResolverWithEmptyType), thisResolverWithEmptyType, otherResolverWithEmptyType, typeStack)
                else -> Result.Failure("Expected array or list type, got ${otherPattern.typeName}")
            }.breadCrumb("[$index]")
        }

        val result = results.find { it is Result.Failure } ?: Result.Success()

        return ConsumeResult(result, emptyList())
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        val resolverWithEmptyType = withEmptyType(pattern, resolver)
        return pattern.listOf(valueList, resolverWithEmptyType)
    }

    override val typeName: String = "list of ${pattern.typeName}"
}

private fun withEmptyType(pattern: Pattern, resolver: Resolver): Resolver {
    val patternSet = pattern.patternSet(resolver)

    val hasXML = patternSet.any { resolvedHop(it, resolver) is XMLPattern }

    val emptyType = if(hasXML) EmptyStringPattern else NullPattern

    return resolver.copy(newPatterns = resolver.newPatterns.plus("(empty)" to emptyType))
}
