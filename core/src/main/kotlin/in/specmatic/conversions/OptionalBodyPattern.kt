package `in`.specmatic.conversions

import `in`.specmatic.core.NoBodyPattern
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.AnyPattern
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.value.Value

data class OptionalBodyPattern(override val pattern: AnyPattern, private val bodyPattern: Pattern) : Pattern by pattern {
    companion object {
        fun fromPattern(bodyPattern: Pattern): OptionalBodyPattern {
            return OptionalBodyPattern(AnyPattern(listOf(bodyPattern, NoBodyPattern)), bodyPattern)
        }
    }


    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        val bodyPatternMatchResult = bodyPattern.matches(sampleData, resolver)

        if(bodyPatternMatchResult is Result.Success)
            return bodyPatternMatchResult

        val nobodyPatternMatchResult = NoBodyPattern.matches(sampleData, resolver)

        if(nobodyPatternMatchResult is Result.Success)
            return nobodyPatternMatchResult

        return bodyPatternMatchResult
    }
}
