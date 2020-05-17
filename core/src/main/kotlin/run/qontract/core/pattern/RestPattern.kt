package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.Value

data class RestPattern(override val pattern: Pattern) : Pattern {
    private val listPattern = ListPattern(pattern)

    override fun matches(sampleData: Value?, resolver: Resolver): Result =
            listPattern.matches(sampleData, resolver)

    override fun generate(resolver: Resolver): JSONArrayValue = listPattern.generate(resolver)
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = pattern.newBasedOn(row, resolver).map { RestPattern(it) }
    override fun parse(value: String, resolver: Resolver): Value = listPattern.parse(value, resolver)
    override fun encompasses(otherPattern: Pattern, resolver: Resolver): Boolean =
            otherPattern is RestPattern && otherPattern.pattern.fitsWithin(pattern.patternSet(resolver), resolver)

    override fun patternSet(resolver: Resolver): List<Pattern> =
            pattern.patternSet(resolver).map { RestPattern(it) }

    override fun encompasses2(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        if(otherPattern !is RestPattern)
            return Result.Failure("Expected rest in string type, got ${otherPattern.typeName}")

        return otherPattern.pattern.fitsWithin2(patternSet(thisResolver), otherResolver, thisResolver)
    }

    override val typeName: String = "the rest are ${pattern.typeName}"
}

private const val REST_SUFFIX = "..."

fun withoutRestToken(pattern: String): String =
        "(" + withoutPatternDelimiters(pattern.trim()).removeSuffix((REST_SUFFIX)) + ")"

fun isRestPattern(it: String): Boolean = it.trim().removePrefix("(").removeSuffix(")").endsWith(REST_SUFFIX)
