package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.utilities.capitalizeFirstChar
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.True
import `in`.specmatic.core.value.Value
import io.ktor.features.*
import io.ktor.http.*

sealed class KeyError {
    abstract val name: String
}

data class MissingKeyError(override val name: String) : KeyError()
data class UnexpectedKeyError(override val name: String) : KeyError()

data class Resolver(val factStore: FactStore = CheckFacts(), val mockMode: Boolean = false, val newPatterns: Map<String, Pattern> = emptyMap(), val findMissingKey: (pattern: Map<String, Any>, actual: Map<String, Any>, UnexpectedKeyCheck) -> KeyError? = ::checkOnlyPatternKeys, val context: Map<String, String> = emptyMap()) {
    constructor(facts: Map<String, Value> = emptyMap(), mockMode: Boolean = false, newPatterns: Map<String, Pattern> = emptyMap()) : this(CheckFacts(facts), mockMode, newPatterns)
    constructor() : this(emptyMap(), false)

    val patterns = builtInPatterns.plus(newPatterns)

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
                    is Result.Failure -> throw ContractException(resultReport(matchResult))
                    else -> fact
                }
            }
        }
    }

    fun addContext(row: Row): Row {
        return row.copy(variables = context)
    }
}

typealias UnexpectedKeyCheck = (Map<String, Any>, Map<String, Any>) -> KeyError?

fun checkOnlyPatternKeys(pattern: Map<String, Any>, actual: Map<String, Any>, lookForUnexpected: UnexpectedKeyCheck = ignoreUnexpectedKeys): KeyError? {
    return pattern.minus("...").keys.find { key ->
        isMissingKey(actual, key)
    }?.let {
        MissingKeyError(it)
    } ?: lookForUnexpected(pattern, actual)
}

fun validateUnexpectedKeys(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? {
    val patternKeys = pattern.minus("...").keys.map { withoutOptionality(it) }
    val actualKeys = actual.keys.map { withoutOptionality(it) }

    return actualKeys.minus(patternKeys).firstOrNull()?.let {
        UnexpectedKeyError(it)
    }
}

internal val checkAllKeys = { pattern: Map<String, Any>, actual: Map<String, Any>, _: Any ->
    pattern.minus("...").keys.find { key -> isMissingKey(actual, key) }?.let { MissingKeyError(it) } ?: validateUnexpectedKeys(pattern, actual)
}

fun missingKeyToResult(keyError: KeyError, keyLabel: String): Result.Failure {
    val message = when(keyError) {
        is MissingKeyError -> "Expected ${keyLabel.lowercase()} named \"${keyError.name}\" was missing"
        is UnexpectedKeyError -> "${keyLabel.lowercase().capitalizeFirstChar()} named \"${keyError.name}\" was unexpected"
    }

    return Result.Failure(message)
}
