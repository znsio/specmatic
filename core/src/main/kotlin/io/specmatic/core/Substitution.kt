package io.specmatic.core

import io.specmatic.core.pattern.ContractException
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

class Substitution(val request: HttpRequest) {
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

    private fun substitute(string: String): String {
        val expressionPath = string.removeSurrounding("{{", "}}")

        val parts = expressionPath.split(".")

        val area = parts.firstOrNull() ?: throw ContractException("The expression $expressionPath was empty")

        return if(area.uppercase() == "REQUEST") {
            val requestPath = parts.drop(1)

            val requestPart = requestPath.firstOrNull() ?: throw ContractException("The expression $expressionPath does not include anything after REQUEST to say what has to be substituted")
            val payloadPath = requestPath.drop(1)

            when(requestPart.uppercase()) {
                "BODY" -> {
                    val requestJSONBody = request.body as? JSONObjectValue
                        ?: throw ContractException("Substitution $string cannot be resolved as the request body is not an object")
                    requestJSONBody.findFirstChildByPath(payloadPath)?.toStringLiteral() ?: throw ContractException("Could not find $string in the request body")
                }
                "HEADERS" -> {
                    val requestHeaders = request.headers
                    val requestHeaderName = payloadPath.joinToString(".")
                    requestHeaders[requestHeaderName]
                        ?: throw ContractException("Substitution $string cannot be resolved as the request header $requestHeaderName cannot be found")
                }
                "QUERY-PARAMS" -> {
                    val requestQueryParams = request.queryParams
                    val requestQueryParamName = payloadPath.joinToString(".")
                    val queryParamPair = requestQueryParams.paramPairs.find { it.first == requestQueryParamName }
                        ?: throw ContractException("Substitution $string cannot be resolved as the request query param $requestQueryParamName cannot be found")

                    queryParamPair.second
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

    fun resolveSubstitutions(map: Map<String, String>): Map<String, String> {
        return map.mapValues {
            substitute(it.value)
        }
    }
}