package io.specmatic.test.asserts

import io.ktor.http.*
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.pattern.DeferredPattern
import io.specmatic.core.pattern.Pattern
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

class AssertPattern(override val prefix: String, override val key: String, val pattern: Pattern, val resolver: Resolver) : Assert {
    override fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val prefixValue = currentFactStore[prefix] ?: return Result.Failure(breadCrumb = prefix, message = "Could not resolve ${prefix.quote()} in current fact store")

        val dynamicList = dynamicAsserts(prefixValue)
        val results = dynamicList.map { newAssert ->
            val finalKey = "${newAssert.prefix}.${newAssert.key}"
            val actualValue = currentFactStore[finalKey] ?: return@map Result.Failure(breadCrumb = finalKey, message = "Could not resolve ${finalKey.quote()} in current fact store")
            runCatching {
                pattern.matches(actualValue, resolver).breadCrumb(finalKey)
            }.getOrElse { e -> Result.Failure(exceptionCauseMessage(e)) }
        }

        return results.toResult()
    }

    override fun dynamicAsserts(prefixValue: Value): List<Assert> {
        return prefixValue.suffixIfMoreThanOne {suffix, _ ->
            AssertPattern(prefix = "$prefix$suffix", key = key, pattern = pattern, resolver = resolver)
        }
    }

    companion object {
        fun parse(prefix: String, key: String, value: Value, resolver: Resolver): AssertPattern? {
            if (value !is StringValue || value.isPatternToken().not()) return null

            val pattern = DeferredPattern(value.string)
            val keyPrefix = prefix.removeSuffix(".${key}")
            return AssertPattern(keyPrefix, key, pattern, resolver)
        }
    }
}