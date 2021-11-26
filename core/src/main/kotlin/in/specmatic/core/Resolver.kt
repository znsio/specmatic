package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.True
import `in`.specmatic.core.value.Value

data class Resolver(
    val factStore: FactStore = CheckFacts(),
    val mockMode: Boolean = false,
    val newPatterns: Map<String, Pattern> = emptyMap(),
    val findKeyErrorCheck: KeyCheck = DefaultKeyCheck,
    val context: Map<String, String> = emptyMap()
) {
    constructor(facts: Map<String, Value> = emptyMap(), mockMode: Boolean = false, newPatterns: Map<String, Pattern> = emptyMap()) : this(CheckFacts(facts), mockMode, newPatterns)
    constructor() : this(emptyMap(), false)

    val patterns = builtInPatterns.plus(newPatterns)

    fun withUnexpectedKeyCheck(unexpectedKeyCheck: UnexpectedKeyCheck): Resolver {
        return this.copy(findKeyErrorCheck = this.findKeyErrorCheck.withUnexpectedKeyCheck(unexpectedKeyCheck))
    }

    fun disableOverrideUnexpectedKeycheck(): Resolver {
        return this.copy(findKeyErrorCheck = this.findKeyErrorCheck.disableOverrideUnexpectedKeycheck())
    }

    fun findKeyError(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? {
        return findKeyErrorCheck.validate(pattern, actual)
    }

    fun matchesPattern(factKey: String?, pattern: Pattern, sampleValue: Value): Result {
        if (mockMode
                && sampleValue is StringValue
                && isPatternToken(sampleValue.string)
                && pattern.encompasses(getPattern(sampleValue.string), this, this).isTrue())
            return Result.Success()

        return pattern.matches(sampleValue, this).ifSuccess {
            if (factKey != null && factStore.has(factKey)) {
                val result = factStore.match(sampleValue, factKey)

                if(result is Result.Failure) {
                    result.reason("Resolver was not able to match fact $factKey with value $sampleValue.")
                }
            }

            Result.Success()
        }
    }

    fun getPattern(patternValue: String): Pattern =
        when {
            isPatternToken(patternValue) -> {
                val resolvedPattern = patterns[patternValue] ?: parsedPattern(patternValue, null)
                when {
                    resolvedPattern is DeferredPattern && resolvedPattern.pattern == patternValue -> throw ContractException("Type $patternValue does not exist")
                    else -> resolvedPattern
                }
            }
            else -> throw ContractException("$patternValue is not a type")
        }

    fun generate(factKey: String, pattern: Pattern): Value {
        if (!factStore.has(factKey))
            return pattern.generate(this)

        return when(val fact = factStore.get(factKey)) {
            is StringValue ->
                try {
                    pattern.parse(fact.string, this)
                } catch (e: Throwable) {
                    throw ContractException("""Value $fact in fact $factKey is not a $pattern""")
                }
            True -> pattern.generate(this)
            else -> {
                when(val matchResult = pattern.matches(fact, this)) {
                    is Result.Failure -> throw ContractException(matchResult.toFailureReport())
                    else -> fact
                }
            }
        }
    }
}
