package `in`.specmatic.core

import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.Value

interface DefaultExampleResolver {
    fun resolveExample(example: String?, pattern: Pattern, resolver: Resolver): Value?
    fun resolveExample(example: List<String?>?, pattern: Pattern, resolver: Resolver): JSONArrayValue?
    fun resolveExample(example: String?, pattern: List<Pattern>, resolver: Resolver): Value?
    fun theDefaultExampleForThisKeyIsNotOmit(valuePattern: Pattern): Boolean
}