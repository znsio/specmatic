package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.Value

data class SlicePattern(override val pattern: Pattern) : Pattern {
    private val listPattern = ListPattern(pattern)

    override fun matches(sampleData: Value?, resolver: Resolver): Result =
            listPattern.matches(sampleData, resolver)

    override fun generate(resolver: Resolver): JSONArrayValue = listPattern.generate(resolver)
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listPattern.newBasedOn(row, resolver)
    override fun parse(value: String, resolver: Resolver): Value = listPattern.parse(value, resolver)
}

private const val SLICE_SUFFIX = "..."

fun withoutSliceToken(pattern: String): String =
        "(" + withoutPatternDelimiter(pattern.trim()).removeSuffix((SLICE_SUFFIX)) + ")"

fun isSlicePattern(it: String): Boolean = it.trim().removePrefix("(").removeSuffix(")").endsWith(SLICE_SUFFIX)
