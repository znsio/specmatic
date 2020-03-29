@file:JvmName("Convert")

package run.qontract.core.pattern

import run.qontract.core.utilities.*
import run.qontract.core.value.*

fun asPattern(patternValue: Any?, key: String?): Pattern =
    when {
        patternValue is LazyPattern -> patternValue.copy(key=key)
        patternValue is Pattern -> patternValue
        patternValue is String && isPatternToken(patternValue) -> LazyPattern(patternValue, key)
        patternValue == null -> NoContentPattern()
        else -> ExactMatchPattern(asValue(patternValue))
    }

fun parsedPattern(rawContent: String, key: String? = null): Pattern {
    return rawContent.trim().let {
        when {
            it.isEmpty() -> NoContentPattern()
            it.startsWith("{") -> JSONObjectPattern(it)
            it.startsWith("[") -> JSONArrayPattern(it)
            it.startsWith("<") -> XMLPattern(it)
            isRepeatingPattern(it) -> RepeatingPattern(it)
            it == "(number)" -> LazyPattern(it, null)
            isPrimitivePattern(it) -> primitivePatterns.getValue(it)
            isPatternToken(it) -> LazyPattern(it, key)
            else -> ExactMatchPattern(StringValue(it))
        }
    }
}

fun asValue(value: Any?): Value = when(value) {
    is Value -> value
    is Number -> NumberValue(value)
    is Boolean -> BooleanValue(value)
    null -> NullValue()
    else -> StringValue(value.toString())
}

fun parsedJSON(content: String): Value? {
    return content.trim().let {
        when {
            it.startsWith("{") -> JSONObjectValue(jsonStringToValueMap(it))
            it.startsWith("[") -> JSONArrayValue(jsonStringToValueArray(it))
            else -> null
        }
    }
}

fun parsedValue(content: String?): Value {
    return content?.trim()?.let {
        when {
            it.startsWith("{") -> JSONObjectValue(jsonStringToValueMap(it))
            it.startsWith("[") -> JSONArrayValue(jsonStringToValueArray(it))
            it.startsWith("<") -> XMLValue(parseXML(it))
            it == "true" -> BooleanValue(true)
            it == "false" -> BooleanValue(false)
            else -> StringValue(it)
        }
    } ?: NoValue()
}
