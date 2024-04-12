package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver

abstract class NegativePatternsTemplate {
    fun negativeBasedOn(patternMap: Map<String, Pattern>, row: Row, resolver: Resolver): Sequence<Map<String, Pattern>> {
        val eachKeyMappedToPatternMap: Map<String, Map<String, Pattern>> = patternMap.mapValues { patternMap }
        val negativePatternsForEachKey: Map<String, Sequence<ReturnValue<Pattern>>> = getNegativePatterns(patternMap, resolver, row)

        val modifiedPatternMap: Map<String, Sequence<Map<String, Sequence<Pattern>>>> = eachKeyMappedToPatternMap.mapValues { (keyToNegate, negatablePatternMap) ->
            val negativePatterns: Sequence<ReturnValue<Pattern>> = negativePatternsForEachKey.getValue(keyToNegate)

            negativePatterns.map { negativePatternR ->
                val negativePattern: Pattern = negativePatternR.value

                negatablePatternMap.mapValues { (key, pattern) ->
                    attempt(breadCrumb = key) {
                        when (key == keyToNegate) {
                            true ->
                                attempt(breadCrumb = "Setting $key to $negativePattern for negative test scenario") {
                                    negativePatternsForKey(key, negativePattern, resolver)
                                }

                            else -> newBasedOn(row, key, pattern, resolver)
                        }
                    }
                }
            }
        }
        if (modifiedPatternMap.values.isEmpty())
            return sequenceOf(emptyMap())
        return modifiedPatternMap.values.asSequence().flatMap { list: Sequence<Map<String, Sequence<Pattern>>> ->
            list.flatMap { patternList(it) }
        }
    }

    fun negativeBasedOnR(
        patternMap: Map<String, Pattern>,
        row: Row,
        resolver: Resolver
    ): Sequence<ReturnValue<Map<String, Pattern>>> {
        val eachKeyMappedToPatternMap: Map<String, Map<String, Pattern>> = patternMap.mapValues { patternMap }
        val negativePatternsForEachKey: Map<String, Sequence<ReturnValue<Pattern>>> = getNegativePatterns(patternMap, resolver, row)

        val modifiedPatternMap: Map<String, Map<String, Sequence<ReturnValue<Pattern>>>> =
            eachKeyMappedToPatternMap.mapValues { (keyToNegate, patterns) ->
                val negativePatterns: Sequence<ReturnValue<Pattern>> = negativePatternsForEachKey.getValue(keyToNegate)

                patterns.mapValues { (key, pattern) ->
                    when (key) {
                        keyToNegate -> negativePatterns
                        else -> newBasedOn(row, key, pattern, resolver).map { HasValue(it) }
                    }
                }
            }

        if (modifiedPatternMap.values.isEmpty())
            return sequenceOf(HasValue(emptyMap()))

        return modifiedPatternMap.values.asSequence().flatMap {
            patternListR(it)
        }

    }

    abstract fun getNegativePatterns(
        patternMap: Map<String, Pattern>,
        resolver: Resolver,
        row: Row
    ): Map<String, Sequence<ReturnValue<Pattern>>>

    abstract fun negativePatternsForKey(
        key: String,
        negativePattern: Pattern,
        resolver: Resolver,
    ): Sequence<Pattern>
}
