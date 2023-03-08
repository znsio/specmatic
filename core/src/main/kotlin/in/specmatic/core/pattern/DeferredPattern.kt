package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.Value

data class DeferredPattern(override val pattern: String, val key: String? = null) : Pattern {
    override fun equals(other: Any?): Boolean = when(other) {
        is DeferredPattern -> other.pattern == pattern
        else -> false
    }
    override fun hashCode(): Int = pattern.hashCode()

    override fun matches(sampleData: Value?, resolver: Resolver) =
            resolver.matchesPattern(key, resolver.getPattern(pattern), sampleData ?: EmptyString)

    override fun generate(resolver: Resolver): Value {
        val resolvedPattern = resolvePattern(resolver)
        return resolver.withCyclePrevention(resolvedPattern) { cyclePreventedResolver ->
            when (key) {
                null -> resolvedPattern.generate(cyclePreventedResolver)
                else -> cyclePreventedResolver.generate(key, resolvedPattern)
            }
        }
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        val resolvedPattern = resolvePattern(resolver)
        return resolver.withCyclePrevention(resolvedPattern) { cyclePreventedResolver ->
            resolvedPattern.newBasedOn(row, cyclePreventedResolver)
        }
    }

    override fun newBasedOn(resolver: Resolver): List<Pattern> {
        val resolvedPattern = resolvePattern(resolver)
        return resolver.withCyclePrevention(resolvedPattern) { cyclePreventedResolver ->
            resolvedPattern.newBasedOn(cyclePreventedResolver)
        }
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return resolver.getPattern(pattern).negativeBasedOn(row, resolver)
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        return thisResolver.getPattern(pattern).encompasses(resolvedHop(otherPattern, otherResolver), thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return resolver.getPattern(pattern).listOf(valueList, resolver)
    }

    override val typeAlias: String? = pattern

    override fun parse(value: String, resolver: Resolver): Value =
        resolver.getPattern(pattern).parse(value, resolver)

    override val typeName: String = withoutPatternDelimiters(pattern)

    fun resolvePattern(resolver: Resolver): Pattern = when(val definedPattern = resolver.getPattern(pattern.trim())) {
        is DeferredPattern -> definedPattern.resolvePattern(resolver)
        else -> definedPattern
    }

    override fun patternSet(resolver: Resolver): List<Pattern> = resolvePattern(resolver).patternSet(resolver)

    override fun toString() = pattern
}

fun resolvedHop(pattern: Pattern, resolver: Resolver): Pattern {
    return when(pattern) {
        is DeferredPattern -> resolvedHop(pattern.resolvePattern(resolver), resolver)
        is LookupRowPattern -> resolvedHop(pattern.pattern, resolver)
        else -> pattern
    }
}
