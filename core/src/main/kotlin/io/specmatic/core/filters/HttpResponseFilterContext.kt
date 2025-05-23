package io.specmatic.core.filters

import io.specmatic.core.HttpResponse

class HttpResponseFilterContext(private val httpResponse: HttpResponse) : FilterContext {
    override fun includes(key: String, values: List<String>): Boolean {
        val filterKey = FilterKeys.fromKey(key)
        return values.any { eachValue ->
            when {
                filterKey == FilterKeys.STATUS -> {
                    httpResponse.status == eachValue.toIntOrNull()
                }
                filterKey == FilterKeys.RESPONSE_CONTENT_TYPE -> {
                    httpResponse.getHeader("CONTENT_TYPE") == eachValue
                }
                key.startsWith(FilterKeys.PARAMETERS_HEADER_KEY.key) -> {
                    val headerKey = key.substringAfter(FilterKeys.PARAMETERS_HEADER_KEY.key)
                    httpResponse.getHeader(headerKey)?.equals(eachValue, ignoreCase = true) ?: false
                }
                else -> false
            }
        }
    }

    override fun compare(filterKey: String, operator: String, filterValue: String): Boolean {
        val key = FilterKeys.fromKey(filterKey)
        return when (key) {
            FilterKeys.STATUS -> evaluateCondition(httpResponse.status, operator, filterValue.toIntOrNull() ?: 0)
            else -> throw IllegalArgumentException("Unknown filter key: $filterKey")
        }
    }
}