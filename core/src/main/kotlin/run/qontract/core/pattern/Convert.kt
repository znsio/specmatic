@file:JvmName("Convert")

package run.qontract.core.pattern

import run.qontract.core.utilities.jsonStringToArray
import run.qontract.core.utilities.jsonStringToMap
import run.qontract.core.utilities.parseXML
import run.qontract.core.value.*

fun asPattern(patternValue: Any?, key: String?): Pattern =
    when {
        patternValue is LazyPattern -> patternValue.copy(key=key)
        patternValue is Pattern -> patternValue
        patternValue is String && isPatternToken(patternValue) -> LazyPattern(patternValue, key)
        patternValue is Map<*, *> -> JSONObjectPattern(patternValue as MutableMap<String, Any?>)
        patternValue is List<*> -> JSONArrayPattern(patternValue as MutableList<Any?>)
        patternValue == null -> NoContentPattern()
        else -> ExactMatchPattern(patternValue)
    }

fun parsedPattern(rawContent: String, key: String? = null): Pattern {
    return rawContent.trim().let {
        when {
            it.isEmpty() -> NoContentPattern()
            it.startsWith("{") -> JSONObjectPattern(it)
            it.startsWith("[") -> JSONArrayPattern(it)
            it.startsWith("<") -> XMLPattern(it)
            isRepeatingPattern(it) -> RepeatingPattern(it)
            isPatternToken(it) -> LazyPattern(it, key)
            else -> ExactMatchPattern(it)
        }
    }
}

fun asValue(value: Any?): Value = when(value) {
    is Value -> value
    is Number -> NumberValue(value)
    is Map<*,*> -> JSONObjectValue((value as Map<String, Any?>).toMutableMap())
    is List<*> -> JSONArrayValue((value as List<Any?>).toMutableList())
    is Boolean -> BooleanValue(value)
    null -> NoValue()
    else -> StringValue(value.toString())
}

fun parsedJSON(content: String): Value? {
    return content.trim().let {
        when {
            it.startsWith("{") -> JSONObjectValue(jsonStringToMap(it))
            it.startsWith("[") -> JSONArrayValue(jsonStringToArray(it))
            else -> null
        }
    }
}

fun parsedValue(content: String?): Value {
    return content?.trim()?.let {
        when {
            it.startsWith("{") -> JSONObjectValue(jsonStringToMap(it))
            it.startsWith("[") -> JSONArrayValue(jsonStringToArray(it))
            it.startsWith("<") -> XMLValue(parseXML(it))
            it == "true" -> BooleanValue(true)
            it == "false" -> BooleanValue(false)
            else -> StringValue(it)
        }
    } ?: NoValue()
}
