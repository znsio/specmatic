package io.specmatic.core

import io.specmatic.core.pattern.*
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

class Substitution(
    val runningRequest: HttpRequest,
    val originalRequest: HttpRequest,
    val httpPathPattern: HttpPathPattern,
    val headersPattern: HttpHeadersPattern,
    val httpQueryParamPattern: HttpQueryParamPattern,
    val body: Pattern,
    val resolver: Resolver
) {
    val variableValues: Map<String, String>

    init {
        val variableValuesFromHeaders = variablesFromMap(runningRequest.headers.filter { it.key in originalRequest.headers }, originalRequest.headers)
        val variableValuesFromQueryParams = variablesFromMap(runningRequest.queryParams.asMap(), originalRequest.queryParams.asMap())

        val runningPathPieces = runningRequest.path!!.split('/').filterNot { it.isBlank() }
        val originalPathPieces = originalRequest.path!!.split('/').filterNot { it.isBlank() }

        val variableValuesFromPath = runningPathPieces.zip(originalPathPieces).map { (runningPiece, originalPiece) ->
            if (!isPatternToken(originalPiece))
                null
            else {
                val pieces = withoutPatternDelimiters(originalPiece).split(':')
                val name = pieces.getOrNull(0)
                    ?: throw ContractException("Could not interpret substituion expression $originalPiece")

                name to runningPiece
            }
        }.filterNotNull().toMap()

        val variableValuesFromRequestBody: Map<String, String> = getVariableValuesFromValue(runningRequest.body, originalRequest.body)

        variableValues = variableValuesFromHeaders + variableValuesFromRequestBody + variableValuesFromQueryParams + variableValuesFromPath
    }

    private fun variableFromString(value: String, originalValue: String): Pair<String, String>? {
        if(!isPatternToken(originalValue))
            return null

        val pieces = withoutPatternDelimiters(originalValue).split(":")

        val name = pieces.getOrNull(0) ?: return null

        return Pair(name, value)
    }

    private fun variablesFromMap(map: Map<String, String>, originalMap: Map<String, String>) = map.entries.map { (key, value) ->
        val originalValue = originalMap.getValue(key)
        variableFromString(value, originalValue)
    }.filterNotNull().toMap()

    private fun getVariableValuesFromValue(value: JSONObjectValue, originalValue: JSONObjectValue): Map<String, String> {
        return originalValue.jsonObject.entries.fold(emptyMap()) { acc, entry ->
            val runningValue = value.jsonObject.getValue(entry.key)
            acc + getVariableValuesFromValue(runningValue, entry.value)
        }
    }

    private fun getVariableValuesFromValue(value: JSONArrayValue, originalValue: JSONArrayValue): Map<String, String> {
        return originalValue.list.foldRightIndexed(emptyMap()) { index: Int, item: Value, acc: Map<String, String> ->
            val runningItem = value.list.get(index)
            acc + getVariableValuesFromValue(runningItem, item)
        }
    }

    private fun getVariableValuesFromValue(value: Value, originalValue: Value): Map<String, String> {
        return when (originalValue) {
            is StringValue -> {
                if(isPatternToken(originalValue.string)) {
                    val pieces = withoutPatternDelimiters(originalValue.string).split(":")
                    val name = pieces.getOrNull(0) ?: return emptyMap()

                    mapOf(name to value.toStringLiteral())
                } else emptyMap()
            }
            is JSONObjectValue -> getVariableValuesFromValue(value as JSONObjectValue, originalValue)
            is JSONArrayValue -> getVariableValuesFromValue(value as JSONArrayValue, originalValue)
            else -> emptyMap()
        }
    }

    fun resolveSubstitutions(value: Value): Value {
        return when(value) {
            is JSONObjectValue -> resolveSubstitutions(value)
            is JSONArrayValue -> resolveSubstitutions(value)
            is StringValue -> {
                if(value.string.startsWith("{{") && value.string.endsWith("}}"))
                    StringValue(substitute(value.string))
                else
                    value
            }
            else -> value
        }
    }

    fun substitute(string: String): String {
        val name = string.trim().removeSurrounding("$(", ")")
        return variableValues[name] ?: throw ContractException("Could not resolve expression $string as no variable by the name $name was found")
    }

    private fun resolveSubstitutions(value: JSONObjectValue): Value {
        return value.copy(
            value.jsonObject.mapValues { entry ->
                resolveSubstitutions(entry.value)
            }
        )
    }

    private fun resolveSubstitutions(value: JSONArrayValue): Value {
        return value.copy(
            value.list.map {
                resolveSubstitutions(it)
            }
        )
    }

    fun resolveHeaderSubstitutions(headers: Map<String, String>, patternMap: Map<String, Pattern>): ReturnValue<Map<String, String>> {
        return headers.mapValues { (key, value) ->
            val returnValue = if(key !in patternMap && "$key?" !in patternMap)
                HasValue(value)
            else {
                val substituteValue = substituteVariableValues(value.trim())

                (patternMap.get(key) ?: patternMap.get("$key?"))?.let { pattern ->
                    try {
                        HasValue(pattern.parse(substituteValue, resolver).toStringLiteral())
                    } catch (e: Throwable) {
                        HasException(e)
                    }
                } ?: HasValue(value)
            }

            returnValue.breadCrumb(key)
        }.mapFold()
    }

    private fun substituteVariableValues(value: String): String {
        if(!isVariableLookup(value))
            return value

        val variableName = value.removeSurrounding("$(", ")")

        return variableValues[variableName] ?: throw ContractException("Could not resolve expression $value as no variable named $variableName was found")
    }

    private fun isVariableLookup(value: String) =
        value.startsWith("$(")
                && value.endsWith(")")
                && !value.contains('[')

    fun substitute(value: Value, pattern: Pattern): ReturnValue<Value> {
        return try {
            if(value !is StringValue || !isVariableLookup(value.string))
                return HasValue(value)

            val updatedString = substitute(value.string)
            HasValue(pattern.parse(updatedString, resolver))
        } catch(e: Throwable) {
            HasException(e)
        }
    }

    private fun hasTemplate(string: String): Boolean {
        return string.startsWith("{{") && string.endsWith("}}")
    }

    fun substitute(string: String, pattern: Pattern): ReturnValue<Value> {
        return try {
            val updatedString = substitute(string)
            HasValue(pattern.parse(updatedString, resolver))
        } catch(e: Throwable) {
            HasException(e)
        }
    }
}