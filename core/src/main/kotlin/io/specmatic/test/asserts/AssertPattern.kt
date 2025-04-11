package io.specmatic.test.asserts

import io.ktor.http.*
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.pattern.DeferredPattern
import io.specmatic.core.pattern.Pattern
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

class AssertPattern(override val keys: List<String>, val pattern: Pattern, val resolver: Resolver) : Assert {
    override fun execute(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val actualValue = currentFactStore[combinedKey] ?: return Result.Failure(
            breadCrumb = combinedKey,
            message = "Could not resolve ${combinedKey.quote()} in response"
        )
        return runCatching { pattern.matches(actualValue, resolver).breadCrumb(combinedKey) }.getOrElse { e -> e.toFailure() }
    }

    override fun dynamicAsserts(currentFactStore: Map<String, Value>, ifNotExists: (String) -> Value): List<Assert> {
        return this.generateDynamicPaths(keys, currentFactStore, ifNotExists = ifNotExists).map { keys ->
            AssertPattern(keys, pattern, resolver)
        }
    }

    companion object {
        fun parse(keys: List<String>, value: Value, resolver: Resolver): AssertPattern? {
            if (value !is StringValue || value.isPatternToken().not()) return null
            val pattern = DeferredPattern(value.string)
            return AssertPattern(keys, pattern, resolver)
        }
    }
}