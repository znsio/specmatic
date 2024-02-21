package `in`.specmatic.conversions

import `in`.specmatic.core.NoBodyPattern
import `in`.specmatic.core.pattern.AnyPattern
import `in`.specmatic.core.pattern.Pattern

class OptionalBodyPattern(override val pattern: AnyPattern) : Pattern by pattern {
    companion object {
        fun fromPattern(bodyPattern: Pattern): OptionalBodyPattern {
            return OptionalBodyPattern(AnyPattern(listOf(bodyPattern, NoBodyPattern)))
        }
    }
}
