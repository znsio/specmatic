package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.Value

data class LazyPattern(override val pattern: String, val key: String? = null) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver) =
            resolver.matchesPattern(key, pattern, sampleData?.value ?: "")

    override fun generate(resolver: Resolver) =
            when (key) {
                null -> resolver.generate(pattern)
                else -> resolver.generate(key, pattern)
            }

    override fun newBasedOn(row: Row, resolver: Resolver): Pattern = resolver.getPattern(pattern).newBasedOn(row, resolver)
}