package run.qontract.core.pattern

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver

internal fun withoutOptionality(key: String) = key.removeSuffix("?")
internal fun isOptional(key: String): Boolean = key.endsWith("?")

internal fun isMissingKey(jsonObject: Map<String, Any?>, key: String) =
        when {
            key.endsWith("?") -> false
            else -> key !in jsonObject
        }

internal fun containsKey(jsonObject: Map<String, Any?>, key: String) =
        when {
            key.endsWith("?") -> key.removeSuffix("?") in jsonObject
            else -> key in jsonObject
        }

internal val primitivePatterns = mapOf(
    "(number)" to NumberTypePattern(),
    "(numericstring)" to NumericStringPattern(),
    "(string)" to StringPattern(),
    "(boolean)" to BooleanPattern())

fun isPrimitivePattern(pattern: Any): Boolean =
    when(pattern) {
        is String -> pattern in primitivePatterns
        else -> false
    }

fun isPatternToken(patternValue: Any?) =
    when(patternValue) {
        is String -> patternValue.startsWith("(") && patternValue.endsWith(")")
        else -> false
    }

fun convertStringToCorrectType(valueOfUnknownType: Any?): Any {
    if(valueOfUnknownType == null)
        throw PatternMismatchException("Cannot convert null values.")

    if (valueOfUnknownType !is String) return valueOfUnknownType
    valueOfUnknownType.trim().let {
        try {
            return it.toInt()
        } catch (ignored: Exception) {
        }
        try {
            return it.toBigInteger()
        } catch (ignored: Exception) {
        }
        try {
            return it.toFloat()
        } catch (ignored: Exception) {
        }
        try {
            return it.toDouble()
        } catch (ignored: Exception) {
        }
        return it
    }
}

fun generateValue(value: Any, resolver: Resolver): Any {
    return if (isPatternToken(value)) {
        findPattern(value as String).generate(resolver).value
    } else value
}

fun isRepeatingPattern(patternValue: Any?): Boolean {
    return patternValue != null && isPatternToken(patternValue) && (patternValue as String).endsWith("*)")
}

fun findPattern(matcherDescriptor: String) =
    primitivePatterns.getOrDefault(matcherDescriptor, UnknownPattern())

fun isLazyPattern(candidate: String): Boolean = isPatternToken(candidate) && candidate !in primitivePatterns
fun removePatternDelimiter(patternValue: String) = patternValue.removeSurrounding("(", ")")
fun nameToPatternSpec(name: String): String {
    return "($name)"
}

fun extractPatternFromRepeatingToken(patternValue: Any): String {
    val patternString = patternValue as String
    return StringBuilder(patternString).deleteCharAt(patternString.length - 2).toString()
}