package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import run.qontract.core.value.True
import run.qontract.core.value.Value

data class Resolver(val factStore: FactStore, val matchPatternInValue: Boolean = false, val patterns: Map<String, Pattern> = emptyMap()) {
    constructor(facts: Map<String, Value> = emptyMap(), matchPattern: Boolean = false, patterns: Map<String, Pattern> = emptyMap()) : this(CheckFacts(facts), matchPattern, patterns)
    constructor() : this(emptyMap(), false)

    fun matchesPattern(factKey: String?, pattern: Pattern, sampleValue: Value): Result {
        if (matchPatternInValue &&
                sampleValue is StringValue && isPatternToken(sampleValue.string) &&
                pattern == getPattern(sampleValue.string))
            return Result.Success()

        when (val result = pattern.matches(sampleValue, this)) {
            is Result.Failure -> {
                return result
            }
        }

        if (factKey != null && factStore.has(factKey)) {
            when(val result = factStore.match(sampleValue, factKey)) {
                is Result.Failure -> result.reason("Resolver was not able to match fact $factKey with value $sampleValue.")
            }
        }

        return Result.Success()
    }

    fun getPattern(patternValue: String): Pattern {
        if (isPatternToken(patternValue = patternValue)) {
            return patterns[patternValue] ?: findPattern(patternValue)
        }

        throw ContractException("Pattern $patternValue does not exist.")
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
            else -> fact
        }
    }
}

fun withNumericStringPattern(resolver: Resolver): Resolver =
        resolver.copy(patterns = resolver.patterns.plus("(number)" to NumericStringPattern()))

fun withNumberTypePattern(resolver: Resolver): Resolver =
        resolver.copy(patterns = resolver.patterns.plus("(number)" to NumberTypePattern()))

