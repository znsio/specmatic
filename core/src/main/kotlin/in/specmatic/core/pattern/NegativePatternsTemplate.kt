package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver

interface NegativePatternsTemplate {
    val stringlyCheck: Boolean
    fun getNegativePatterns(
        patternMap: Map<String, Pattern>,
        resolver: Resolver,
        row: Row
    ): Map<String, List<Pattern>>

    fun negativeBasedOn(patternMap: Map<String, Pattern>, row: Row, resolver: Resolver): List<Map<String, Pattern>> {
        val eachKeyMappedToPatternMap = patternMap.mapValues { patternMap }
        val negativePatternsMap = getNegativePatterns(patternMap, resolver, row)

        val modifiedPatternMap: Map<String, List<Map<String, List<Pattern>>>> = eachKeyMappedToPatternMap.mapValues { (keyToNegate, patterns) ->
            val negativePatterns = negativePatternsMap[keyToNegate]
            negativePatterns!!.map { negativePattern ->
                patterns.mapValues { (key, pattern) ->
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
            return listOf(emptyMap())
        return modifiedPatternMap.values.map { list: List<Map<String, List<Pattern>>> ->
            list.toList().map { patternList(it) }.flatten()
        }.flatten()
    }

    fun negativeBasedOn(patternMap: Map<String, Pattern>, resolver: Resolver): List<Map<String, Pattern>> {
        val patternCollection = patternMap.mapValues { (key, pattern) ->
            attempt(breadCrumb = key) {
                val resolvedPattern = resolvedHop(pattern, resolver)
                if(stringlyCheck && resolvedPattern is StringPattern) {
                    emptyList()
                }
                else if (stringlyCheck && isScalar(resolvedPattern)) {
                    negativeBasedOn(key, resolvedPattern, resolver).filterNot { it is NullPattern  }
                }
                else {
                    negativeBasedOn(key, resolvedPattern, resolver)
                }
            }
        }

        return patternValues(patternCollection)
    }


    fun negativePatternsForKey(
        key: String,
        negativePattern: Pattern,
        resolver: Resolver,
    ): List<Pattern>
}

fun negativeBasedOn(key: String, pattern: Pattern, resolver: Resolver): List<Pattern> {
    return resolver.withCyclePrevention(pattern, isOptional(key)) { cyclePreventedResolver ->
        pattern.newBasedOn(cyclePreventedResolver)
    }?:
    // Handle cycle (represented by null value) by using empty list for optional properties
    listOf()
}

fun isScalar(resolvedPattern: Pattern) =
    resolvedPattern is ScalarType
            || (resolvedPattern is QueryParameterScalarPattern && resolvedPattern.pattern is ScalarType)

