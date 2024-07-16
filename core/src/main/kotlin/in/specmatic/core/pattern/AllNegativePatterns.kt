package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.pattern.config.NegativePatternConfiguration

class AllNegativePatterns : NegativePatternsTemplate() {

    override fun getNegativePatterns(
        patternMap: Map<String, Pattern>,
        resolver: Resolver,
        row: Row,
        config: NegativePatternConfiguration
    ): Map<String, Sequence<ReturnValue<Pattern>>> {
        return patternMap.mapValues { (key, pattern) ->
            val resolvedPattern = resolvedHop(pattern, resolver)
            resolvedPattern.negativeBasedOn(
                row.stepDownOneLevelInJSONHierarchy(withoutOptionality(key)),
                resolver,
                config
            )
        }
    }
}