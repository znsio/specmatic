package run.qontract.core.pattern

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

private val builtInPatterns = mapOf(
    "(number)" to NumberTypePattern(),
    "(numericstring)" to NumericStringPattern(),
    "(string)" to StringPattern(),
    "(boolean)" to BooleanPattern(),
    "(null)" to NullPattern,
    "(datetime)" to DateTimePattern,
    "(url)" to URLPattern(URLScheme.EITHER),
    "(url http)" to URLPattern(URLScheme.HTTP),
    "(url https)" to URLPattern(URLScheme.HTTPS),
    "(url path)" to URLPattern(URLScheme.PATH))

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

fun findBuiltInPattern(patternString: String): Pattern =
        when {
            isPatternToken(patternString) -> builtInPatterns.getOrElse(patternString) {
                if(patternString.contains(":")) {
                    val patternParts = withoutPatternDelimiters(patternString).split(":").map { DeferredPattern(withPatternDelimiters(it.trim())) }

                    if(patternParts.size == 2) {
                        DictionaryPattern(patternParts[0], patternParts[1])
                    } else throw ContractException("Type $patternString does not exist.")
                } else throw ContractException("Type $patternString does not exist.")
            }
            else -> throw ContractException("Type $patternString is not a type specifier.")
        }

fun withoutPatternDelimiters(patternValue: String) = patternValue.removeSurrounding("(", ")")
fun withPatternDelimiters(name: String): String = "($name)"

fun withoutRepeatingToken(patternValue: Any): String {
    val patternString = (patternValue as String).trim()
    return "(" + withoutPatternDelimiters(patternString).removeSuffix("*") + ")"
}

fun isRepeatingPattern(patternValue: Any?): Boolean =
        patternValue != null && isPatternToken(patternValue) && (patternValue as String).endsWith("*)")
