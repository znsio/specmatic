package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.Value

interface Pattern {
    fun matches(sampleData: Value?, resolver: Resolver): Result
    fun generate(resolver: Resolver): Value
    fun newBasedOn(row: Row, resolver: Resolver): List<Pattern>
    fun parse(value: String, resolver: Resolver): Value

    fun patternSet(resolver: Resolver): List<Pattern> = listOf(this)

    fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack = emptySet()): Result
    fun fitsWithin(otherPatterns: List<Pattern>, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val myPatternSet = patternSet(thisResolver)

        val result = myPatternSet.map { my ->
            val encompassResult = otherPatterns.asSequence().map { other ->
                biggerEncompassesSmaller(other, my, thisResolver, otherResolver, typeStack)
            }

            encompassResult.find { it is Result.Success } ?: encompassResult.first()
        }

        return result.find { it is Result.Failure } ?: Result.Success()
    }

    fun listOf(valueList: List<Value>, resolver: Resolver): Value

    val typeAlias: String?
    val typeName: String
    val pattern: Any
}
