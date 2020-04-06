package run.qontract.core.pattern

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver
import run.qontract.core.value.StringValue

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
    "(boolean)" to BooleanPattern(),
    "(null)" to NullPattern)

fun isPrimitivePattern(pattern: Any): Boolean =
    when(pattern) {
        is String -> pattern in primitivePatterns
        else -> false
    }

fun isPatternToken(patternValue: Any?) =
    when(patternValue) {
        is String -> patternValue.startsWith("(") && patternValue.endsWith(")")
        is StringValue -> patternValue.string.startsWith("(") && patternValue.string.endsWith(")")
        else -> false
    }

fun generateValue(value: String, resolver: Resolver): String {
    return if (isPatternToken(value)) {
        findPattern(value).generate(resolver).toStringValue()
    } else value
}

fun findPattern(matcherDescriptor: String): Pattern =
        primitivePatterns.getOrElse(matcherDescriptor) { throw ContractParseException("Pattern $matcherDescriptor does not exist.") }


fun withoutPatternDelimiters(patternValue: String) = patternValue.removeSurrounding("(", ")")
fun withPatternDelimiters(name: String): String = "($name)"

fun withoutRepeatingToken(patternValue: Any): String {
    val patternString = (patternValue as String).trim()
    return "(" + withoutPatternDelimiters(patternString).removeSuffix("*") + ")"
//    return StringBuilder(patternString).deleteCharAt(patternString.length - 2).toString()
}

fun isRepeatingPattern(patternValue: Any?): Boolean =
        patternValue != null && isPatternToken(patternValue) && (patternValue as String).endsWith("*)")
