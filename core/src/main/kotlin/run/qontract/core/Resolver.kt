package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import run.qontract.core.value.True
import run.qontract.core.value.Value

data class Resolver(val factStore: FactStore = CheckFacts(), val matchPatternInValue: Boolean = false, val newPatterns: Map<String, Pattern> = emptyMap(), val findMissingKey: (pattern: Map<String, Any>, actual: Map<String, Any>) -> Pair<String?, String?>? = checkOnlyPatternKeys ) {
    constructor(facts: Map<String, Value> = emptyMap(), matchPattern: Boolean = false, newPatterns: Map<String, Pattern> = emptyMap()) : this(CheckFacts(facts), matchPattern, newPatterns)
    constructor() : this(emptyMap(), false)

    val patterns = builtInPatterns.plus(newPatterns)

    fun matchesPattern(factKey: String?, pattern: Pattern, sampleValue: Value): Result {
        if (matchPatternInValue
                && sampleValue is StringValue
                && isPatternToken(sampleValue.string)
                && pattern.encompasses(getPattern(sampleValue.string), this, this).isTrue())
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
                    is Result.Failure -> throw ContractException(resultReport(matchResult))
                    else -> fact
                }
            }
        }
    }
}

fun withNumberTypePattern(resolver: Resolver): Resolver =
        resolver.copy(newPatterns = resolver.newPatterns.plus("(number)" to NumberPattern))

val checkOnlyPatternKeys = { pattern: Map<String, Any>, actual: Map<String, Any> ->
    pattern.keys.find { key -> isMissingKey(actual, key) }?.let { Pair(it, null) }
}

val checkAllKeys = { pattern: Map<String, Any>, actual: Map<String, Any> ->
    pattern.keys.find { key -> isMissingKey(actual, key) }?.let { Pair(it, null) } ?: actual.keys.find { key ->
        val keyWithoutOptionality = withoutOptionality(key)
        key !in pattern && "$keyWithoutOptionality?" !in pattern
    }?.let { Pair(null, it) }
}

fun missingKeyToResult(missingKey: Pair<String?, String?>, keyName: String): Result.Failure {
    val (expectedMissingInActual, actualMissingInExpected) = missingKey

    return Result.Failure(when {
        expectedMissingInActual != null -> "Expected ${keyName.toLowerCase()} $expectedMissingInActual was missing"
        actualMissingInExpected != null -> "${keyName.toLowerCase().capitalize()} $actualMissingInExpected was unexpected"
        else -> throw ContractException("Missing key result ($missingKey) is confusing")
    })
}