package `in`.specmatic.core.pattern

interface ScalarType

fun scalarAnnotation(pattern: Pattern, negativePatterns: Sequence<Pattern>): Sequence<ReturnValue<Pattern>> {
    return negativePatterns.map {
        HasValue(it, "Expected type in spec was ${pattern.typeName}, trying out a ${it.typeName}")
    }
}


