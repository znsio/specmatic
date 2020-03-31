package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.value.NullValue
import run.qontract.core.value.Value

data class LookupPattern(override val pattern: String, val key: String? = null) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver) =
            resolver.matchesPattern(key, pattern, sampleData ?: NullValue())

    override fun generate(resolver: Resolver) =
            when (key) {
                null -> resolver.generate(pattern)
                else -> resolver.generate(key, pattern)
            }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> =
        resolver.getPattern(pattern).newBasedOn(row, resolver)

    override fun parse(value: String, resolver: Resolver): Value =
        resolver.getPattern(pattern).parse(value, resolver)

    fun resolvePattern(resolver: Resolver): Pattern = when(val definedPattern = resolver.getPattern(pattern)) {
        is LookupPattern -> definedPattern.resolvePattern(resolver)
        else -> definedPattern
    }
}
