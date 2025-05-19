package io.specmatic.core.filters

import io.specmatic.core.HttpRequest

class HttpRequestFilterContext(private val httpRequest: HttpRequest) : FilterContext {
    override fun includes(key: String, values: List<String>): Boolean {
        return values.any { eachValue ->
            when {
                key == "METHOD" -> {
                    httpRequest.method.equals(eachValue, ignoreCase = true)
                }

                key == "PATH" -> {
                    httpRequest.path == eachValue
                }

                key == "REQUEST-HEADER" -> {
                    httpRequest.containsHeader(eachValue)
//                    httpRequest.headers.keys.caseInsensitiveContains(eachValue)
                }

                else -> throw IllegalArgumentException("Unknown filter parameter name: $key")
            }
        }
    }

    override fun compare(filterKey: String, operator: String, filterValue: String): Boolean {
        throw UnsupportedOperationException("Compare is not supported for HttpRequestFilterContext")
    }

}
