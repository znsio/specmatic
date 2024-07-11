package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.config.NegativePatternConfiguration

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