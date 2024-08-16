package io.specmatic.core

import io.specmatic.core.pattern.*
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

class Substitution(
    val request: HttpRequest,
    val httpPathPattern: HttpPathPattern,
    val headersPattern: HttpHeadersPattern,
    val httpQueryParamPattern: HttpQueryParamPattern,
    val body: Pattern,
    val resolver: Resolver
) {
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
        val expressionPath = string.removeSurrounding("{{", "}}")

        val parts = expressionPath.split(".")

        val area = parts.firstOrNull() ?: throw ContractException("The expression $expressionPath was empty")

        return if(area.uppercase() == "REQUEST") {
            val requestPath = parts.drop(1)

            val requestPart = requestPath.firstOrNull() ?: throw ContractException("The expression $expressionPath does not include anything after REQUEST to say what has to be substituted")
            val payloadPath = requestPath.drop(1)

            val payloadKey = payloadPath.joinToString(".")

            when (requestPart.uppercase()) {
                "BODY" -> {
                    val requestJSONBody = request.body as? JSONObjectValue
                        ?: throw ContractException("Substitution $string cannot be resolved as the request body is not an object")
                    requestJSONBody.findFirstChildByPath(payloadPath)?.toStringLiteral()
                        ?: throw ContractException("Could not find $string in the request body")
                }

                "HEADERS" -> {
                    val requestHeaders = request.headers
                    val requestHeaderName = payloadKey
                    requestHeaders[requestHeaderName]
                        ?: throw ContractException("Substitution $string cannot be resolved as the request header $requestHeaderName cannot be found")
                }

                "QUERY-PARAMS" -> {
                    val requestQueryParams = request.queryParams
                    val requestQueryParamName = payloadKey
                    val queryParamPair = requestQueryParams.paramPairs.find { it.first == requestQueryParamName }
                        ?: throw ContractException("Substitution $string cannot be resolved as the request query param $requestQueryParamName cannot be found")

                    queryParamPair.second
                }

                "PATH" -> {
                    val indexOfPathParam = httpPathPattern.pathSegmentPatterns.indexOfFirst { it.key == payloadKey }

                    if (indexOfPathParam < 0) throw ContractException("Could not find path param named $string")

                    (request.path ?: "").split("/").let {
                        if (it.firstOrNull() == "")
                            it.drop(1)
                        else
                            it
                    }.get(indexOfPathParam)
                }

                else -> string
            }
        }
        else
            string
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

    fun resolveHeaderSubstitutions(map: Map<String, String>, patternMap: Map<String, Pattern>): ReturnValue<Map<String, String>> {
        return map.mapValues { (key, value) ->
            val string = substitute(value)

            val returnValue = (patternMap.get(key) ?: patternMap.get("$key?"))?.let { pattern ->
                try {
                    HasValue(pattern.parse(string, resolver).toStringLiteral())
                } catch(e: Throwable) {
                    HasException(e)
                }
            } ?: HasValue(value)

            returnValue.breadCrumb(key)
        }.mapFold()
    }

    fun substitute(value: Value, pattern: Pattern): ReturnValue<Value> {
        return try {
            if(value !is StringValue || !hasTemplate(value.string))
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