package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver

class StrictNegativePatterns : NegativePatternsTemplate {
    override fun negativePatternsForKey(
        key: String,
        negativePattern: Pattern,
        resolver: Resolver,
    ): List<Pattern> {
        return newBasedOn(Row(), key, negativePattern, resolver)
    }

    override val stringlyCheck: Boolean
        get() = false

    override fun getNegativePatterns(
        patternMap: Map<String, Pattern>,
        resolver: Resolver,
        row: Row
    ): Map<String, List<Pattern>> {
        return patternMap.mapValues { (key, pattern) ->
            val resolvedPattern = resolvedHop(pattern, resolver)
            resolvedPattern.negativeBasedOn(row.stepDownOneLevelInJSONHierarchy(withoutOptionality(key)), resolver)
        }
    }
}