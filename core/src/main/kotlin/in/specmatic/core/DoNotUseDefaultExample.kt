package `in`.specmatic.core

import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.Value

object DoNotUseDefaultExample : DefaultExampleResolver {
    override fun resolveExample(example: String?, pattern: Pattern, resolver: Resolver): Value? {
        return null
    }

    override fun resolveExample(example: List<String?>?, pattern: Pattern, resolver: Resolver): JSONArrayValue? {
        return null
    }

    override fun resolveExample(example: String?, pattern: List<Pattern>, resolver: Resolver): Value? {
        return null
    }

    override fun theDefaultExampleForThisKeyIsNotOmit(valuePattern: Pattern): Boolean {
        return true
    }

}