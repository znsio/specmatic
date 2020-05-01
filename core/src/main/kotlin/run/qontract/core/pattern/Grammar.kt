package run.qontract.core.pattern

import run.qontract.core.utilities.jsonStringToValueArray
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.value.*

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
    "(number)" to NumberTypePattern,
    "(string)" to StringPattern,
    "(boolean)" to BooleanPattern,
    "(null)" to NullPattern,
    "(datetime)" to DateTimePattern,
    "(url)" to URLPattern(URLScheme.EITHER),
    "(url http)" to URLPattern(URLScheme.HTTP),
    "(url https)" to URLPattern(URLScheme.HTTPS),
    "(url path)" to URLPattern(URLScheme.PATH))

fun isBuiltInPattern(pattern: Any): Boolean =
    when(pattern) {
        is String -> when {
            pattern in builtInPatterns -> true
            isPatternToken(pattern) -> when {
                ":" in pattern || " in " in pattern -> true
                else -> false
            }
            else -> false
        }
        else -> false
    }

fun isPatternToken(patternValue: Any?) =
    when(patternValue) {
        is String -> patternValue.startsWith("(") && patternValue.endsWith(")")
        is StringValue -> patternValue.string.startsWith("(") && patternValue.string.endsWith(")")
        else -> false
    }

internal fun getBuiltInPattern(patternString: String): Pattern =
        when {
            isPatternToken(patternString) -> builtInPatterns.getOrElse(patternString) {
                when {
                    patternString.contains(":") -> {
                        val patternParts = withoutPatternDelimiters(patternString).split(":").map { parsedPattern(withPatternDelimiters(it.trim())) }

                        if(patternParts.size == 2) {
                            DictionaryPattern(patternParts[0], patternParts[1])
                        } else throw ContractException("Type $patternString does not exist.")
                    }
                    patternString.contains(" in ") -> {
                        val patternParts = withoutPatternDelimiters(patternString).split(" in ").map { it.trim().toLowerCase() }

                        if(patternParts.size != 2)
                            throw ContractException("$patternString seems incomplete")

                        if(patternParts.get(1) != "string")
                            throw ContractException("Only string is supported for declaring a pattern in a pattern")

                        PatternInStringPattern(parsedPattern(withPatternDelimiters(patternParts.get(0))))
                    }
                    else -> throw ContractException("Type $patternString does not exist.")
                }
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

fun stringToPattern(patternValue: String, key: String?): Pattern =
        when {
            isPatternToken(patternValue) -> DeferredPattern(patternValue, key)
            else -> ExactMatchPattern(StringValue(patternValue))
        }

fun parsedPattern(rawContent: String, key: String? = null): Pattern {
    return rawContent.trim().let {
        when {
            it.isEmpty() -> NoContentPattern
            it.startsWith("{") -> JSONObjectPattern(it)
            it.startsWith("[") -> JSONArrayPattern(it)
            it.startsWith("<") -> XMLPattern(it)
            isNullablePattern(it) -> AnyPattern(listOf(NullPattern, parsedPattern(withoutNullToken(it))))
            isRestPattern(it) -> RestPattern(parsedPattern(withoutRestToken(it)))
            isRepeatingPattern(it) -> ListPattern(parsedPattern(withoutRepeatingToken(it)))
            it == "(number)" -> DeferredPattern(it, null)
            isBuiltInPattern(it) -> getBuiltInPattern(it)
            isPatternToken(it) -> DeferredPattern(it, key)
            else -> ExactMatchPattern(StringValue(it))
        }
    }
}

fun parsedJSONStructure(content: String): Value {
    return content.trim().let {
        when {
            it.startsWith("{") -> JSONObjectValue(jsonStringToValueMap(it))
            it.startsWith("[") -> JSONArrayValue(jsonStringToValueArray(it))
            else -> throw ContractException("Expected json, actual $content.")
        }
    }
}

fun parsedValue(content: String?): Value {
    return content?.trim()?.let {
        when {
            it.startsWith("{") -> JSONObjectValue(jsonStringToValueMap(it))
            it.startsWith("[") -> JSONArrayValue(jsonStringToValueArray(it))
            it.startsWith("<") -> XMLValue(it)
            it == "true" -> BooleanValue(true)
            it == "false" -> BooleanValue(false)
            else -> StringValue(it)
        }
    } ?: EmptyString
}
