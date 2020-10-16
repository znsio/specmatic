package run.qontract.core.pattern

import run.qontract.core.utilities.jsonStringToValueArray
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.value.*

const val XML_ATTR_OPTIONAL_SUFFIX = ".opt"
const val DEFAULT_OPTIONAL_SUFFIX = "?"

internal fun withoutOptionality(key: String): String {
    return when {
        key.endsWith(DEFAULT_OPTIONAL_SUFFIX) -> key.removeSuffix(DEFAULT_OPTIONAL_SUFFIX)
        key.endsWith(XML_ATTR_OPTIONAL_SUFFIX) -> key.removeSuffix(XML_ATTR_OPTIONAL_SUFFIX)
        else -> key
    }
}
internal fun isOptional(key: String): Boolean = key.endsWith(DEFAULT_OPTIONAL_SUFFIX) || key.endsWith(XML_ATTR_OPTIONAL_SUFFIX)

internal fun isMissingKey(jsonObject: Map<String, Any?>, key: String) =
        when {
            isOptional(key) -> false
            else -> key !in jsonObject && "$key?" !in jsonObject && "$key:" !in jsonObject
        }

internal fun containsKey(jsonObject: Map<String, Any?>, key: String) =
        when {
            isOptional(key) -> withoutOptionality(key) in jsonObject
            else -> key in jsonObject
        }

internal val builtInPatterns = mapOf(
    "(number)" to NumberPattern,
    "(string)" to StringPattern,
    "(boolean)" to BooleanPattern,
    "(null)" to NullPattern,
    "(empty)" to EmptyStringPattern,
    "(datetime)" to DateTimePattern,
    "(url)" to URLPattern(URLScheme.EITHER),
    "(url-http)" to URLPattern(URLScheme.HTTP),
    "(url-https)" to URLPattern(URLScheme.HTTPS),
    "(url-path)" to URLPattern(URLScheme.PATH))

fun isBuiltInPattern(pattern: Any): Boolean =
    when(pattern) {
        is String -> when {
            pattern in builtInPatterns -> true
            isPatternToken(pattern) -> when {
                isLookupRowPattern(pattern) || " in " in pattern || isDictionaryPattern(pattern) -> true
                else -> false
            }
            else -> false
        }
        else -> false
    }

fun isDictionaryPattern(pattern: String): Boolean {
    val pieces = withoutPatternDelimiters(pattern).trim().split("\\s+".toRegex())

    return when(pieces[0]) {
        "dictionary" -> pieces.size == 3
        else -> false
    }
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
                    isDictionaryPattern(patternString) ->  {
                        val pieces = breakIntoParts(withoutPatternDelimiters(patternString), "\\s+".toRegex(), 3, "Dictionary type must have 3 parts: type name, key and value")

                        val patterns = pieces.slice(1..2).map { parsedPattern(withPatternDelimiters(it.trim())) }
                        DictionaryPattern(patterns[0], patterns[1])
                    }
                    isLookupRowPattern(patternString) -> {
                        val patternParts = breakIntoParts(withoutPatternDelimiters(patternString), ":", 2, "Type with key must have the key before the colon and the type specification after it. Got $patternString")

                        val (key, patternSpec) = patternParts
                        val pattern = parsedPattern(withPatternDelimiters(patternSpec))

                        LookupRowPattern(pattern, key)
                    }
                    patternString.contains(" in ") -> {
                        val patternParts = breakIntoParts(withoutPatternDelimiters(patternString), " in ", 2, "$patternString seems incomplete").map { it.trim().toLowerCase() }

                        if(patternParts[1] != "string")
                            throw ContractException("""Types can only be declared to be "in string", you probably meant (${patternParts[1]} in string)""")

                        PatternInStringPattern(parsedPattern(withPatternDelimiters(patternParts[0])))
                    }
                    else -> throw ContractException("Type $patternString does not exist.")
                }
            }
            else -> throw ContractException("Type $patternString is not a type specifier.")
        }

fun breakIntoParts(text: String, delimiter: Regex, count: Int, errorMessage: String): List<String> {
    val pieces = text.split(delimiter)
    if(pieces.size != count)
        throw ContractException(errorMessage)
    return pieces
}

fun breakIntoParts(text: String, delimiter: String, count: Int, errorMessage: String): List<String> {
    val pieces = text.split(delimiter)
    if(pieces.size != count)
        throw ContractException(errorMessage)
    return pieces
}

fun withoutPatternDelimiters(patternValue: String) = patternValue.removeSurrounding("(", ")")
fun withPatternDelimiters(name: String): String = "($name)"

fun withoutListToken(patternValue: Any): String {
    val patternString = (patternValue as String).trim()
    return "(" + withoutPatternDelimiters(patternString).removeSuffix("*") + ")"
}

fun isRepeatingPattern(patternValue: Any?): Boolean =
        patternValue != null && isPatternToken(patternValue) && (patternValue as String).endsWith("*)")

fun stringToPattern(patternValue: String, key: String?): Pattern =
        when {
            isPatternToken(patternValue) -> DeferredPattern(patternValue, key)
            else -> ExactValuePattern(StringValue(patternValue))
        }

fun parsedPattern(rawContent: String, key: String? = null, typeAlias: String? = null): Pattern {
    return rawContent.trim().let {
        when {
            it.isEmpty() -> EmptyStringPattern
            it.startsWith("{") -> toJSONObjectPattern(it, typeAlias = typeAlias)
            it.startsWith("[") -> JSONArrayPattern(it, typeAlias = typeAlias)
            it.startsWith("<") -> XMLPattern(it, typeAlias = typeAlias)
            isPatternToken(it) -> when {
                isLookupRowPattern(it) -> {
                    val (pattern, lookupKey) = parseLookupRowPattern(it)
                    LookupRowPattern(parsedPattern(pattern, typeAlias = typeAlias), lookupKey)
                }
                isOptionalValuePattern(it) -> AnyPattern(listOf(DeferredPattern("(empty)", key), parsedPattern(withoutNullToken(it), typeAlias = typeAlias)))
                isRestPattern(it) -> RestPattern(parsedPattern(withoutRestToken(it), typeAlias = typeAlias))
                isRepeatingPattern(it) -> ListPattern(parsedPattern(withoutListToken(it), typeAlias = typeAlias))
                it == "(number)" -> DeferredPattern(it, null)
                isBuiltInPattern(it) -> getBuiltInPattern(it)
                else -> DeferredPattern(it, key)
            }
            else -> ExactValuePattern(StringValue(it))
        }
    }
}

fun parseLookupRowPattern(token: String): Pair<String, String> {
    val parts = withoutPatternDelimiters(token).split(":".toRegex(), 2).map { it.trim() }

    val key = parts.first()
    val patternToken = parts[1]

    return Pair(withPatternDelimiters(patternToken), key)
}

fun isLookupRowPattern(token: String): Boolean {
    val parts = withoutPatternDelimiters(token).split(":".toRegex())

    return when (parts.size) {
        2 -> true
        else -> false
    }
}

fun parsedJSON(content: String): Value {
    return content.trim().let {
        when {
            it.startsWith("{") -> try { JSONObjectValue(jsonStringToValueMap(it)) } catch(e: Throwable) { throw ContractException("Could not parse json object, got error: ${e.localizedMessage ?: e.message}") }
            it.startsWith("[") -> try { JSONArrayValue(jsonStringToValueArray(it)) } catch(e: Throwable) { throw ContractException("Could not parse json array, got error: ${e.localizedMessage ?: e.message}") }
            else -> throw ContractException("Expected json, actual $content.")
        }
    }
}

fun parsedValue(content: String?): Value {
    return content?.trim()?.let {
        try {
            when {
                it.startsWith("{") -> JSONObjectValue(jsonStringToValueMap(it))
                it.startsWith("[") -> JSONArrayValue(jsonStringToValueArray(it))
                it.startsWith("<") -> XMLNode(it)
                else -> StringValue(it)
            }
        } catch(e: Throwable) {
            StringValue(it)
        }
    } ?: EmptyString
}
