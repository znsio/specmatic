package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import run.qontract.core.value.True
import run.qontract.core.value.Value

data class Resolver(val factStore: FactStore, val matchPatternInValue: Boolean = false, var customPatterns: HashMap<String, Pattern> = HashMap()) {
    constructor(facts: HashMap<String, Value> = HashMap(), matchPattern: Boolean = false) : this(CheckFacts(facts), matchPattern)
    constructor() : this(HashMap(), false)

    fun makeCopy(): Resolver = copy(customPatterns = HashMap(customPatterns))
    fun makeCopy(matchPattern: Boolean, newPatterns: Map<String, Pattern>): Resolver = copy(matchPatternInValue = matchPattern, customPatterns = HashMap(customPatterns.plus(newPatterns)))

    fun matchesPattern(factKey: String?, pattern: Pattern, sampleValue: Value): Result {
        if (matchPatternInValue &&
                sampleValue is StringValue && isPatternToken(sampleValue.string) &&
                pattern == getPattern(sampleValue.string))
            return Result.Success()

        when (val result = pattern.matches(sampleValue, this)) {
            is Result.Failure -> {
                return result.add("""Expected $pattern, actual $sampleValue""")
            }
        }

        if (factKey != null && factStore.has(factKey)) {
            when(val result = factStore.match(sampleValue, factKey)) {
                is Result.Failure -> result.add("Resolver was not able to match fact $factKey with value $sampleValue.")
            }
        }

        return Result.Success()
    }

    fun addCustomPattern(spec: String, pattern: Pattern) {
        this.customPatterns[spec] = pattern
    }

    fun addCustomPatterns(patterns: java.util.HashMap<String, Pattern>) {
        customPatterns.putAll(patterns)
    }

    fun getPattern(patternValue: String): Pattern {
        if (isPatternToken(patternValue = patternValue)) {
            return customPatterns[patternValue] ?: findPattern(patternValue)
        }

        throw ContractParseException("Pattern $patternValue does not exist.")
    }

    fun generate(factKey: String, pattern: Pattern): Value {
        if (!factStore.has(factKey))
            return pattern.generate(this)

        return when(val fact = factStore.get(factKey)) {
            is StringValue ->
                try {
                    pattern.parse(fact.string, this)
                } catch (e: Throwable) {
                    throw ContractParseException("Fact $fact against key $factKey is not a $pattern")
                }
            True -> pattern.generate(this)
            else -> fact
        }
    }
}