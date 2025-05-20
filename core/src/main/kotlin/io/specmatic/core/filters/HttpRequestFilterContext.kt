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
                key == "PARAMETERS.HEADER" -> {
                    httpRequest.containsHeader(eachValue)
                }
                else -> {
                    false
                }
            }
        }
    }

    override fun compare(filterKey: String, operator: String, filterValue: String): Boolean {
        throw UnsupportedOperationException("Compare is not supported for HttpRequestFilterContext")
    }

}
