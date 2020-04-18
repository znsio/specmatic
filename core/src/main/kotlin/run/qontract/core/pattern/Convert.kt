@file:JvmName("Convert")

package run.qontract.core.pattern

import run.qontract.core.utilities.*
import run.qontract.core.value.*

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

fun parsedJSON(content: String): Value {
    return content.trim().let {
        when {
            it.startsWith("{") -> JSONObjectValue(jsonStringToValueMap(it))
            it.startsWith("[") -> JSONArrayValue(jsonStringToValueArray(it))
            else -> NullValue
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
