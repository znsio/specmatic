package run.qontract.core.pattern

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

internal val builtInPatterns = mapOf(
    "(number)" to NumberTypePattern(),
    "(numericstring)" to NumericStringPattern(),
    "(string)" to StringPattern(),
    "(boolean)" to BooleanPattern(),
    "(null)" to NullPattern,
    "(datetime)" to DateTimePattern,
    "(http-url)" to URLPattern(URLScheme.HTTP),
    "(https-url)" to URLPattern(URLScheme.HTTPS),
    "(url-path)" to URLPattern(URLScheme.PATH))

fun isBuiltInPattern(pattern: Any): Boolean =
    when(pattern) {
        is String -> pattern in builtInPatterns
        else -> false
    }

fun isPatternToken(patternValue: Any?) =
    when(patternValue) {
        is String -> patternValue.startsWith("(") && patternValue.endsWith(")")
        is StringValue -> patternValue.string.startsWith("(") && patternValue.string.endsWith(")")
        else -> false
    }

fun generateValue(key: String, value: String, resolver: Resolver): String {
    return if (isPatternToken(value)) {
        resolver.generate(key, findPattern(value)).toStringValue()
    } else value
}

fun findPattern(matcherDescriptor: String): Pattern =
        builtInPatterns.getOrElse(matcherDescriptor) { throw ContractException("Pattern $matcherDescriptor does not exist.") }


fun withoutPatternDelimiters(patternValue: String) = patternValue.removeSurrounding("(", ")")
fun withPatternDelimiters(name: String): String = "($name)"

fun withoutRepeatingToken(patternValue: Any): String {
    val patternString = (patternValue as String).trim()
    return "(" + withoutPatternDelimiters(patternString).removeSuffix("*") + ")"
}

fun isRepeatingPattern(patternValue: Any?): Boolean =
        patternValue != null && isPatternToken(patternValue) && (patternValue as String).endsWith("*)")
