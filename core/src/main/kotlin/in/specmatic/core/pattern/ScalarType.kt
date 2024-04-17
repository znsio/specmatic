package `in`.specmatic.core.pattern

interface ScalarType

fun scalarAnnotation(pattern: Pattern, negativePatterns: Sequence<Pattern>): Sequence<ReturnValue<Pattern>> {
    return negativePatterns.map {
        HasValue(it, "Schema in spec was ${pattern.typeName}, mutating to ${it.typeName}")
    }
}


