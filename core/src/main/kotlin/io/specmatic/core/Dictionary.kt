package io.specmatic.core

import io.specmatic.core.log.logger
import io.specmatic.core.pattern.*
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value
import java.io.File

class Dictionary private constructor(private val value: Map<String, Value>) {

    fun plus(other: Map<String, Value>): Dictionary {
        return Dictionary(value + other)
    }

    fun containsKey(key: String): Boolean {
        return key in value
    }

    fun getRawValue(key: String): Value {
        if (key !in value) throw IllegalArgumentException("Dictionary does not contain key: $key")
        return value.getValue(key)
    }

    fun getDefaultValueFor(pattern: Pattern, resolver: Resolver): Value? {
        val lookupKey = withPatternDelimiters(pattern.typeName)
        return getValueFor(lookupKey, pattern, resolver)?.withDefault(null) { it }
    }

    fun getValueFor(lookup: String, pattern: Pattern, resolver: Resolver): ReturnValue<Value>? {
        val dictionaryValue = value[lookup] ?: return null
        val valueToMatch = getValueToMatch(dictionaryValue, pattern, resolver) ?: return null

        return runCatching {
            val result = pattern.matches(valueToMatch, resolver)
            if (result is Result.Failure && resolver.isNegative) return@runCatching null
            result.toReturnValue(valueToMatch, "Invalid Dictionary value at \"$lookup\"")
        }.getOrElse(::HasException)
    }

    private fun getValueToMatch(value: Value, pattern: Pattern, resolver: Resolver): Value? {
        if (value !is JSONArrayValue) return value
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
                Dictionary(dictionaryContent)
            }.getOrElse { e ->
                logger.debug(e)
                throw ContractException(
                    breadCrumb = file.path,
                    errorMessage = "Could not parse dictionary file ${file.path}, it must be a valid JSON/YAML object"
                )
            }
        }

        fun from(valueMap: Map<String, Value>): Dictionary {
            return Dictionary(valueMap)
        }

        fun empty(): Dictionary {
            return Dictionary(emptyMap())
        }
    }
}