package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result

class NegativeNonStringlyPatterns : NegativePatternsTemplate() {

    override fun negativePatternsForKey(
        key: String,
        negativePattern: Pattern,
        resolver: Resolver,
    ): List<Pattern> {
        return if (patternIsEnum(negativePattern, resolver)) {
            negativeBasedOnForEnum(negativePattern)
        } else {
            newBasedOn(Row(), key, negativePattern, resolver)
        }
    }

    private fun patternIsEnum(pattern: Pattern, resolver: Resolver): Boolean {
        val resolvedPattern = resolvedHop(pattern, resolver)

        return resolvedPattern is EnumPattern
    }

    override fun getNegativePatterns(
        patternMap: Map<String, Pattern>,
        resolver: Resolver,
        row: Row
    ): Map<String, List<Pattern>> {
        return patternMap.mapValues { (key, pattern) ->
            val resolvedPattern = resolvedHop(pattern, resolver)

            resolvedPattern
                .negativeBasedOn(row.stepDownOneLevelInJSONHierarchy(withoutOptionality(key)), resolver)
                .filterNot {
                    isStringly(resolvedPattern, it, resolver)
                }.filterNot {
                    it is NullPattern
                }
        }
    }

    private fun isStringly(
        resolvedPattern: Pattern,
        it: Pattern,
        resolver: Resolver
    ) = resolvedPattern.matches(it.generate(resolver).toStringValue(), resolver) is Result.Success

    private fun negativeBasedOnForEnum(pattern: Pattern): List<Pattern> {
        val enumPattern = (pattern as EnumPattern).pattern
        val firstEnumOption = enumPattern.pattern.first() as ExactValuePattern
        val valueOfFirstEnumOption = firstEnumOption.pattern
        val patternOfFirstValue = valueOfFirstEnumOption.type()
        return listOf(patternOfFirstValue)
    }
}