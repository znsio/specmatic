package io.specmatic.core.filters

import io.specmatic.mock.ScenarioStub
import java.util.regex.Pattern
import javax.activation.MimeType

class HttpStubFilterContext(private val scenario: ScenarioStub) : FilterContext {
    override fun includes(key: String, values: List<String>): Boolean {
        val filterKey = FilterKeys.fromKey(key)
        return values.any { eachValue ->
            when {
                filterKey == FilterKeys.PATH -> {
                    eachValue == scenario.request.path || matchesPath(eachValue, scenario.request.path ?: "")
                }
                filterKey == FilterKeys.PARAMETERS_HEADER -> {
                    scenario.request.headers.keys.caseInsensitiveContains(eachValue)
                }
                key.startsWith(FilterKeys.PARAMETERS_HEADER_KEY.key) -> {
                    val queryKey = key.substringAfter(FilterKeys.PARAMETERS_HEADER_KEY.key).substringBefore("=")
                    val queryValue = eachValue.substringAfter("=")
                    scenario.request.headers[queryKey] == queryValue
                }
                filterKey == FilterKeys.PARAMETERS_QUERY -> {
                    scenario.request.queryParams.keys.caseSensitiveContains(eachValue)
                }
                key.startsWith(FilterKeys.PARAMETERS_QUERY_KEY.key) -> {
                    val queryKey = key.substringAfter(FilterKeys.PARAMETERS_QUERY_KEY.key).substringBefore("=")
                    val queryValue = eachValue.substringAfter("=")
                    scenario.request.queryParams.paramPairs.any {
                        it.first == queryKey && it.second == queryValue
                    }
                }
                filterKey == FilterKeys.REQUEST_BODY_CONTENT_TYPE -> {
                    try {
                        MimeType(scenario.request.body.httpContentType).match(MimeType(eachValue))
                    } catch (_: Exception) {
                        false
                    }
                }
                filterKey == FilterKeys.RESPONSE_CONTENT_TYPE -> {
                    try {
                        MimeType(scenario.response.body.httpContentType).match(MimeType(eachValue))
                    } catch (_: Exception) {
                        false
                    }
                }
                filterKey == FilterKeys.METHOD -> {
                    scenario.request.method.equals(eachValue, ignoreCase = true)
                }
                filterKey == FilterKeys.STATUS -> {
                    scenario.response.status == eachValue.toIntOrNull()
                }
                else -> true
            }
        }
    }

    override fun compare(filterKey: String, operator: String, filterValue: String): Boolean {
        val key = FilterKeys.fromKey(filterKey)
        return when (key) {
            FilterKeys.STATUS -> evaluateCondition(scenario.response.status, operator, filterValue.toIntOrNull() ?: 0)
            else -> throw IllegalArgumentException("Unknown filter key: $filterKey")
        }
    }

    private fun matchesPath(value: String, scenarioValue: String): Boolean {
        return value.contains("*") &&
                Pattern.compile(
                    value.replace("*", ".*")
                ).matcher(scenarioValue).matches()
    }
}