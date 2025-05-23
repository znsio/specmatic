package io.specmatic.core.filters

import io.specmatic.core.Scenario
import java.util.regex.Pattern
import javax.activation.MimeType


class HttpFilterContext(private val scenario: Scenario) : FilterContext {
    override fun includes(key: String, values: List<String>): Boolean {
        val filterKey = FilterKeys.fromKey(key)
        return values.any { eachValue ->
            when {
                filterKey == FilterKeys.PATH -> {
                    eachValue == scenario.path || matchesPath(eachValue, scenario.path)
                }
                filterKey == FilterKeys.PARAMETERS_HEADER -> {
                    scenario.httpRequestPattern.getHeaderKeys().caseInsensitiveContains(eachValue)
                }
                key.startsWith(FilterKeys.PARAMETERS_HEADER_KEY.key) -> {
                    val queryKey = key.substringAfter(FilterKeys.PARAMETERS_HEADER_KEY.key).substringBefore("=")
                    val queryValue = eachValue.substringAfter("=")
                    scenario.examples.any { eachExample->
                        eachExample.rows.any { eachRow ->
                            eachRow.containsField(queryKey) && eachRow.getField(queryKey) == queryValue
                        }
                    }
                }
                filterKey == FilterKeys.PARAMETERS_QUERY -> {
                    scenario.httpRequestPattern.getQueryParamKeys().caseSensitiveContains(eachValue)
                }
                key.startsWith(FilterKeys.PARAMETERS_QUERY_KEY.key) -> {
                    val queryKey = key.substringAfter(FilterKeys.PARAMETERS_QUERY_KEY.key).substringBefore("=")
                    val queryValue = eachValue.substringAfter("=")
                    scenario.examples.any { eachExample->
                        eachExample.rows.any { eachRow ->
                            eachRow.containsField(queryKey) && eachRow.getField(queryKey) == queryValue
                        }
                    }
                }
                filterKey == FilterKeys.PARAMETERS_PATH -> {
                    scenario.httpRequestPattern.httpPathPattern?.pathSegmentPatterns?.map{it.key}?.contains(eachValue) ?: false
                }
                filterKey == FilterKeys.REQUEST_BODY_CONTENT_TYPE -> {
                    try {
                        MimeType(scenario.httpRequestPattern.headersPattern.contentType).match(MimeType(eachValue))
                    } catch (_: Exception) {
                        false
                    }
                }
                filterKey == FilterKeys.RESPONSE_CONTENT_TYPE -> {
                    try {
                        MimeType(scenario.httpResponsePattern.headersPattern.contentType).match(MimeType(eachValue))
                    } catch (_: Exception) {
                        false
                    }
                }
                filterKey == FilterKeys.METHOD -> {
                    scenario.method.equals(eachValue, ignoreCase = true)
                }
                filterKey == FilterKeys.STATUS -> {
                    scenario.status == eachValue.toIntOrNull()
                }
                filterKey == FilterKeys.EXAMPLE_NAME -> {
                    scenario.examples.any { example ->
                        example.rows.any { eachRow -> eachRow.name == eachValue }
                    }
                }
                filterKey == FilterKeys.TAGS -> {
                    scenario.operationMetadata?.tags?.contains(eachValue) ?: false
                }
                filterKey == FilterKeys.SUMMARY -> {
                    scenario.operationMetadata?.summary.equals(eachValue, ignoreCase = true)
                }
                filterKey == FilterKeys.OPERATION_ID -> {
                    scenario.operationMetadata?.operationId == eachValue
                }
                filterKey == FilterKeys.DESCRIPTION -> {
                    scenario.operationMetadata?.description.equals(eachValue, ignoreCase = true)
                }
                else -> false
            }
        }
    }

    override fun compare(filterKey: String, operator: String, filterValue: String): Boolean {
        if (filterKey.uppercase() == "STATUS") {
            return evaluateCondition(scenario.status, operator, filterValue.toIntOrNull() ?: 0)
        } else {
            throw IllegalArgumentException("Unknown filter key: $filterKey")
        }
    }

    private fun matchesPath(value: String, scenarioValue: String): Boolean {
        return value.contains("*") &&
                Pattern.compile(
                    value.replace("{", "\\(").replace("}", ".*\\)").replace("*", ".*")
                ).matcher(scenarioValue).matches()
    }

}

internal fun Iterable<String>.caseInsensitiveContains(needle: String): Boolean =
    this.any { haystack -> haystack.lowercase().trim().removeSuffix("?") == needle.lowercase() }

internal fun Iterable<String>.caseSensitiveContains(needle: String): Boolean =
    this.any { haystack -> haystack.trim().removeSuffix("?") == needle }
