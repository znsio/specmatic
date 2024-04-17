package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver

class AllNegativePatterns : NegativePatternsTemplate() {
    override fun negativePatternsForKey(
        key: String,
        negativePattern: Pattern,
        resolver: Resolver,
    ): Sequence<Pattern> {
        return newBasedOn(Row(), key, negativePattern, resolver)
    }

    override fun getNegativePatterns(
        patternMap: Map<String, Pattern>,
        resolver: Resolver,
        row: Row
    ): Map<String, Sequence<ReturnValue<Pattern>>> {
        return patternMap.mapValues { (key, pattern) ->
            val resolvedPattern = resolvedHop(pattern, resolver)
            resolvedPattern.negativeBasedOn(row.stepDownOneLevelInJSONHierarchy(withoutOptionality(key)), resolver)
        }
    }
}