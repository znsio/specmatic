package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

class Resolver(val serverStateMatch: ServerStateMatch, var matchPattern: Boolean = false) {
    var customPatterns: HashMap<String, Pattern> = HashMap()

    constructor(serverState: HashMap<String, Any> = HashMap(), matchPattern: Boolean = false) : this(ServerStateStringValueMatch(serverState), matchPattern) {}
    constructor() : this(HashMap<String, Any>(), false)

    fun copy(): Resolver {
        val newResolver = Resolver(serverStateMatch, false)
        newResolver.customPatterns = HashMap(customPatterns)
        newResolver.matchPattern = matchPattern

        return newResolver
    }

    fun matchesPatternValue(serverStateKey: String?, patternValue: Any, sampleValue: Any): Result {
        if (matchPattern && sampleValue is Value && patternValue == sampleValue.value)
            return Result.Success()

        if (patternValue is String && isPatternToken(patternValue)) {
            val pattern = when (patternValue) {
                in customPatterns -> customPatterns[patternValue]
                else -> findPattern(patternValue)
            }

            when (val result = pattern?.matches(asValue(sampleValue), this)) {
                is Result.Failure -> {
                    return result.add("""Expected $patternValue, actual $sampleValue""")
                }
            }

            if (serverStateKey != null && serverStateKey in serverStateMatch) {
                when(val result = serverStateMatch.match(sampleValue, serverStateKey)) {
                    is Result.Failure -> result.add("Resolver was not able to match $serverStateKey with value $sampleValue")
                }
            }

            return Result.Success()
        }

        return when ((patternValue == sampleValue)) {
            true -> Result.Success()
            else -> Result.Failure("Expected $patternValue, actual $sampleValue")
        }
    }

    fun matchesPattern(serverStateKey: String?, pattern: Pattern, sampleValue: Value): Result {
        if (matchPattern && sampleValue is StringValue && pattern == parsedPattern(sampleValue.string))
            return Result.Success()

        when (val result = pattern.matches(sampleValue, this)) {
            is Result.Failure -> {
                return result.add("""Expected $pattern, actual $sampleValue""")
            }
        }

        if (serverStateKey != null && serverStateKey in serverStateMatch) {
            when(val result = serverStateMatch.match(sampleValue, serverStateKey)) {
                is Result.Failure -> result.add("Resolver was not able to match $serverStateKey with value $sampleValue")
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

        throw ContractParseException("Pattern $patternValue does not exit.")
    }

    fun generate(key: String, pattern: Pattern): Value {
        if (serverStateMatch.contains(key)) {
            val nativeValue = serverStateMatch.get(key)

            if (nativeValue != null && nativeValue != true) {
                val value = pattern.parse(nativeValue as String, this)
                when (matchesPattern(null, pattern, value)) {
                    is Result.Success -> return value
                    else -> throw ContractParseException("$nativeValue doesn't match $pattern")
                }
            }
        }

        return pattern.generate(this)
    }

    fun generateFromAny(key: String, patternValue: Any): Value {
        if (serverStateMatch.contains(key)) {
            val value = serverStateMatch.get(key)
            if (value != null && value != true) {
                when (matchesPatternValue(null, patternValue, value)) {
                    is Result.Success -> return asValue(value)
                    else -> throw ContractParseException("$value doesn't match $patternValue")
                }
            }
        }

        return generateFromAny(patternValue)
    }

    fun generateFromAny(patternValue: Any): Value {
        if (patternValue !is String)
            return StringValue()

        return getPattern(patternValue).generate(this)
    }

    fun generateValue(key: String, parameterPattern: String?): Any {
        val parameterValue = parameterPattern as String
        if (serverStateMatch.contains(key)) {
            val value = serverStateMatch.get(key)
            if (isPatternToken(parameterValue) && value != null && value != true)
                when (matchesPatternValue(null, parameterValue, value)) {
                    is Result.Success -> return asValue(value)
                    else -> throw ContractParseException("$value doesn't match $parameterPattern")
                }
        }

        return generateValue(parameterPattern, this)
    }
}