package io.specmatic.core.filters

import io.specmatic.mock.ScenarioStub
import java.util.regex.Pattern
import javax.activation.MimeType


class HttpStubFilterContext(private val scenario: ScenarioStub) : FilterContext {
    override fun includes(key: String, values: List<String>): Boolean {
        return values.any { eachValue ->
            when {
                key == "PATH" -> {
                    eachValue == scenario.request.path || matchesPath(eachValue, scenario.request.path ?: "")
                }
                key == "PARAMETERS.HEADER" -> {
                    scenario.request.headers.keys.caseInsensitiveContains(eachValue)
                }
                key.startsWith("PARAMETERS.HEADER.") -> {
                    // This only applies to examples
                    val queryKey = key.substringAfter("PARAMETERS.HEADER.").substringBefore("=")
                    val queryValue = eachValue.substringAfter("=")
                    scenario.request.headers[queryKey] == queryValue
                }
                key == "PARAMETERS.QUERY" -> {
                    scenario.request.queryParams.keys.caseSensitiveContains(eachValue)
                }
                key.startsWith("PARAMETERS.QUERY.") -> {
                    // This only applies to examples
                    val queryKey = key.substringAfter("PARAMETERS.QUERY.").substringBefore("=")
                    val queryValue = eachValue.substringAfter("=")
                    scenario.request.queryParams.paramPairs.any { it ->
                        it.first == queryKey && it.second == queryValue
                    }
                }
                key == "REQUEST-BODY.CONTENT-TYPE" -> {
                    try {
                        MimeType(scenario.request.body.httpContentType).match(MimeType(eachValue))
                    } catch (_: Exception) {
                        false
                    }
                }
                key == "RESPONSE.CONTENT-TYPE" -> {
                    try {
                        MimeType(scenario.response.body.httpContentType).match(MimeType(eachValue))
                    } catch (_: Exception) {
                        false
                    }
                }
                key == "METHOD" -> {
                    scenario.request.method.equals(eachValue, ignoreCase = true)
                }
                key == "STATUS" -> {
                    scenario.response.status == eachValue.toIntOrNull()
                }
                else -> {
                    true
                }
            }
        }
    }

    override fun compare(filterKey: String, operator: String, filterValue: String): Boolean {
        if (filterKey.uppercase() == "STATUS") {
            return evaluateCondition(scenario.response.status, operator, filterValue.toIntOrNull() ?: 0)
        } else {
            throw IllegalArgumentException("Unknown filter key: $filterKey")
        }
    }



    private fun matchesPath(value: String, scenarioValue: String): Boolean {
        return value.contains("*") &&
                Pattern.compile(
                    value.replace("*", ".*")
                ).matcher(scenarioValue).matches()
    }

}