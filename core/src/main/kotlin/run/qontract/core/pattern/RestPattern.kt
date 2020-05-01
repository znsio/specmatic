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
    override fun matchesPattern(pattern: Pattern, resolver: Resolver): Boolean =
            pattern is RestPattern && pattern.pattern.matchesPattern(this.pattern, resolver)

    override val displayName: String = "the rest are ${pattern.displayName}"
}

private const val REST_SUFFIX = "..."

fun withoutRestToken(pattern: String): String =
        "(" + withoutPatternDelimiters(pattern.trim()).removeSuffix((REST_SUFFIX)) + ")"

fun isRestPattern(it: String): Boolean = it.trim().removePrefix("(").removeSuffix(")").endsWith(REST_SUFFIX)
