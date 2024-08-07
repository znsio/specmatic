package io.specmatic.core.pattern

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.pattern.config.NegativePatternConfiguration

class NegativeNonStringlyPatterns : NegativePatternsTemplate() {

    override fun getNegativePatterns(
        patternMap: Map<String, Pattern>,
        resolver: Resolver,
        row: Row,
        config: NegativePatternConfiguration
    ): Map<String, Sequence<ReturnValue<Pattern>>> {
        return patternMap.mapValues { (key, pattern) ->
            val resolvedPattern = resolvedHop(pattern, resolver)

            resolvedPattern
                .negativeBasedOn(
                    row.stepDownOneLevelInJSONHierarchy(withoutOptionality(key)),
                    resolver,
                    config
                )
                .filterValueIsNot {
                    isStringly(resolvedPattern, it, resolver)
                }.filterValueIsNot {
                    it is NullPattern
                }
        }
    }

    private fun isStringly(
        resolvedPattern: Pattern,
        it: Pattern,
        resolver: Resolver
    ) = resolvedPattern.matches(it.generate(resolver).toStringValue(), resolver) is Result.Success

}