package `in`.specmatic.core

import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.isOptional

fun <T> combinations(list: List<T>): List<List<T>> {
    if (list.isEmpty()) return listOf(emptyList())
    val element = list.first()
    val subCombinations = combinations(list.drop(1))
    return subCombinations + subCombinations.map { it + element }
}

fun <ValueType> combinatorialCombinations(map: Map<String, ValueType>): List<List<String>> {
    return combinations(map.keys.toList())
}

fun allOrNothingTestCount(pattern: Map<String, Pattern>, resolver: Resolver): ULong {
    val optional = pattern.filter { isOptional(it.key) }
    val mandatory = pattern.filter { !isOptional(it.key) }

    val mandatoryTestCount =
        mandatory.values.fold(1.toULong()) { acc, mandatoryPattern -> acc * mandatoryPattern.testCount(resolver) }

    if (optional.isEmpty())
        return mandatoryTestCount

    val optionalTestCount =
        optional.values.fold(1.toULong()) { acc, optionalPattern -> acc * optionalPattern.testCount(resolver) }
    return mandatoryTestCount + (mandatoryTestCount * optionalTestCount)
}
