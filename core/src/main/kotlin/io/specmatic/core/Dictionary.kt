package io.specmatic.core

import io.specmatic.core.log.logger
import io.specmatic.core.pattern.*
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value
import java.io.File

data class Dictionary(private val data: Map<String, Value>, private val focusedData: Map<String, Value> = emptyMap()) {
    private val defaultData: Map<String, Value> = data.filterKeys(::isPatternToken)

    fun plus(other: Map<String, Value>): Dictionary {
        return copy(data = data + other)
    }

    fun containsKey(key: String): Boolean {
        return key in data
    }

    fun getRawValue(key: String): Value {
        if (key !in data) throw IllegalArgumentException("Dictionary does not contain key: $key")
        return data.getValue(key)
    }

    fun focusIntoSchema(pattern: Pattern, key: String, resolver: Resolver): Dictionary {
        return focusInto(pattern, key, resolver, data, preserve = false)
    }

    fun focusIntoProperty(pattern: Pattern, key: String, resolver: Resolver, preserve: Boolean): Dictionary {
        return focusInto(pattern, key, resolver, focusedData, preserve)
    }

    fun getDefaultValueFor(pattern: Pattern, resolver: Resolver): Value? {
        val lookupKey = withPatternDelimiters(pattern.typeName)
        val defaultValue = defaultData[lookupKey] ?: return null
        return getReturnValueFor(lookupKey, defaultValue, pattern, resolver)?.withDefault(null) { it }
    }

    fun getValueFor(lookup: String, pattern: Pattern, resolver: Resolver): ReturnValue<Value>? {
        val tailEndKey = lookup.tailEndKey()
        val dictionaryValue = focusedData[tailEndKey] ?: return null
        return getReturnValueFor(lookup, dictionaryValue, pattern, resolver)
    }

    private fun focusInto(pattern: Pattern, key: String, resolver: Resolver, storeToUse: Map<String, Value>, preserve: Boolean): Dictionary {
        val rawValue = storeToUse[key] ?: return resetFocus(preserve)
        val valueToFocusInto = getValueToMatch(rawValue, pattern, resolver, true) ?: return resetFocus(preserve)
        val dataToFocusInto = (valueToFocusInto as? JSONObjectValue)?.jsonObject ?: storeToUse
        return copy(focusedData = dataToFocusInto)
    }

    private fun getReturnValueFor(lookup: String, value: Value, pattern: Pattern, resolver: Resolver): ReturnValue<Value>? {
        val valueToMatch = getValueToMatch(value, pattern, resolver) ?: return null
        return runCatching {
            val result = pattern.matches(valueToMatch, resolver)
            if (result is Result.Failure && resolver.isNegative) return@runCatching null
            result.toReturnValue(valueToMatch, "Invalid Dictionary value at \"$lookup\"")
        }.getOrElse(::HasException)
    }

    private fun getValueToMatch(value: Value, pattern: Pattern, resolver: Resolver, overrideNestedCheck: Boolean = false): Value? {
        if (value !is JSONArrayValue) return value.takeIf { pattern.isScalar(resolver) || overrideNestedCheck }
        if (pattern !is ListPattern) return value.list.randomOrNull()

        val patternDepth = calculateDepth<Pattern>(pattern) { (resolvedHop(it, resolver) as? ListPattern)?.pattern?.let(::listOf) }
        val valueDepth = calculateDepth<Value>(value) { (it as? JSONArrayValue)?.list }
        return when {
            valueDepth > patternDepth -> value.list.randomOrNull()
            else -> value
        }
    }

    private fun <T> calculateDepth(data: T, getChildren: (T) -> List<T>?): Int {
        val children = getChildren(data) ?: return 0
        return when {
            children.isEmpty() -> 1
            else -> 1 + children.maxOf { calculateDepth(it, getChildren) }
        }
    }

    private fun String.tailEndKey(): String = substringAfterLast(".")

    private fun Pattern.isScalar(resolver: Resolver): Boolean {
        val resolved = resolvedHop(this, resolver)
        return resolved is ScalarType || resolved is URLPathSegmentPattern || resolved is QueryParameterScalarPattern
    }

    private fun resetFocus(preserve: Boolean): Dictionary = copy(focusedData = focusedData.takeIf { preserve }.orEmpty())

    companion object {
        fun from(file: File): Dictionary {
            if (!file.exists()) throw ContractException(
                breadCrumb = file.path,
                errorMessage = "Expected dictionary file at ${file.path}, but it does not exist"
            )

            if (!file.isFile) throw ContractException(
                breadCrumb = file.path,
                errorMessage = "Expected dictionary file at ${file.path} to be a file"
            )

            return runCatching {
                logger.log("Using dictionary file ${file.path}")
                val dictionaryContent = readValueAs<JSONObjectValue>(file).jsonObject
                from(dictionaryContent)
            }.getOrElse { e ->
                logger.debug(e)
                throw ContractException(
                    breadCrumb = file.path,
                    errorMessage = "Could not parse dictionary file ${file.path}, it must be a valid JSON/YAML object"
                )
            }
        }

        fun from(valueMap: Map<String, Value>): Dictionary {
            val nestedFormat = DataRepresentation.from(valueMap).toValue()
            return Dictionary(data = nestedFormat.jsonObject)
        }

        fun empty(): Dictionary {
            return Dictionary(data = emptyMap())
        }
    }
}