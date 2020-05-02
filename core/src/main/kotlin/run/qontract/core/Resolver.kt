package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import run.qontract.core.value.True
import run.qontract.core.value.Value

data class Resolver(val factStore: FactStore, val matchPatternInValue: Boolean = false, val newPatterns: Map<String, Pattern> = emptyMap(), val findMissingKey: (pattern: Map<String, Pattern>, actual: Map<String, Value>) -> String? = checkOnlyPatternKeys ) {
    constructor(facts: Map<String, Value> = emptyMap(), matchPattern: Boolean = false, newPatterns: Map<String, Pattern> = emptyMap()) : this(CheckFacts(facts), matchPattern, newPatterns)
    constructor() : this(emptyMap(), false)

    val patterns = builtInPatterns.plus(newPatterns)

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

    fun getPattern(patternValue: String): Pattern =
        when {
            isPatternToken(patternValue) -> {
                patterns[patternValue] ?: parsedPattern(patternValue, null)
            }
            else -> throw ContractException("Type $patternValue does not exist.")
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
                    is Result.Failure -> throw ContractException(resultReport(matchResult))
                    else -> fact
                }
            }
        }
    }
}

fun withNumericStringPattern(resolver: Resolver): Resolver =
        resolver.copy(newPatterns = resolver.newPatterns.plus("(number)" to NumericStringPattern))

fun withNumberTypePattern(resolver: Resolver): Resolver =
        resolver.copy(newPatterns = resolver.newPatterns.plus("(number)" to NumberTypePattern))

val checkOnlyPatternKeys = { pattern: Map<String, Pattern>, actual: Map<String, Value> ->
    pattern.keys.find { key -> isMissingKey(actual, key) }
}

val checkAllKeys = { pattern: Map<String, Pattern>, actual: Map<String, Value> ->
    pattern.keys.find { key -> isMissingKey(actual, key) } ?: actual.keys.find { key ->
        key !in pattern
    }
}
