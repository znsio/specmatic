package io.specmatic.core.filters

import io.specmatic.core.HttpRequest

class HttpRequestFilterContext(private val httpRequest: HttpRequest) : FilterContext {
    override fun includes(key: String, values: List<String>): Boolean {
        val filterKey = FilterKeys.fromKey(key)
        return values.any { eachValue ->
            when {
                filterKey == FilterKeys.METHOD -> {
                    httpRequest.method.equals(eachValue, ignoreCase = true)
                }
                filterKey == FilterKeys.PATH -> {
                    httpRequest.path == eachValue
                }
                filterKey == FilterKeys.PARAMETERS_HEADER -> {
                    httpRequest.containsHeader(eachValue)
                }
                else -> false
            }
        }
    }

    override fun compare(filterKey: String, operator: String, filterValue: String): Boolean {
        throw UnsupportedOperationException("Compare is not supported for HttpRequestFilterContext")
    }
}