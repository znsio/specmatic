package io.specmatic.core.filters

import io.specmatic.core.HttpResponse

class HttpResponseFilterContext(private val httpResponse: HttpResponse) : FilterContext {
    override fun includes(key: String, values: List<String>): Boolean {
        return values.any { eachValue ->
            when {
                key == "STATUS" -> {
                    httpResponse.status == eachValue.toIntOrNull()
                }

                key == "RESPONSE-HEADER" -> {
                    httpResponse.containsHeader(eachValue)
                }

                key.startsWith("RESPONSE-HEADER.") -> {
                    val headerKey = key.substringAfter("RESPONSE-HEADER.").substringBefore("=")
                    val headerValue = eachValue.substringAfter("=")
                    httpResponse.getHeader(headerKey) == headerValue
                }

                else -> throw IllegalArgumentException("Unknown filter parameter name: $key")
            }
        }
    }

    override fun compare(
        filterKey: String,
        operator: String,
        filterValue: String
    ): Boolean {
        if (filterKey.uppercase() == "STATUS") {
            return evaluateCondition(httpResponse.status, operator, filterValue.toIntOrNull() ?: 0)
        } else {
            throw IllegalArgumentException("Unknown filter key: $filterKey")
        }
    }

}
