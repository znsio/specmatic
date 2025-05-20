package io.specmatic.core.filters

import io.specmatic.core.Scenario
import java.util.regex.Pattern
import javax.activation.MimeType


class HttpFilterContext(private val scenario: Scenario) : FilterContext {
    override fun includes(key: String, values: List<String>): Boolean {
        return values.any { eachValue ->
            when {
                key == "PATH" -> {
                    eachValue == scenario.path || matchesPath(eachValue, scenario.path)
                }
                key == "PARAMETERS.HEADER" -> {
                    scenario.httpRequestPattern.getHeaderKeys().caseInsensitiveContains(eachValue)
                }
                key.startsWith("PARAMETERS.HEADER.") -> {
                    // This only applies to examples
                    val queryKey = key.substringAfter("PARAMETERS.HEADER.").substringBefore("=")
                    val queryValue = eachValue.substringAfter("=")
                    scenario.examples.any { eachExample->
                        eachExample.rows.any { eachRow ->
                            eachRow.containsField(queryKey) && eachRow.getField(queryKey) == queryValue
                        }
                    }
                }
                key == "PARAMETERS.QUERY" -> {
                    scenario.httpRequestPattern.getQueryParamKeys().caseSensitiveContains(eachValue)
                }
                key.startsWith("PARAMETERS.QUERY.") -> {
                    // This only applies to examples
                    val queryKey = key.substringAfter("PARAMETERS.QUERY.").substringBefore("=")
                    val queryValue = eachValue.substringAfter("=")
                    scenario.examples.any { eachExample->
                        eachExample.rows.any { eachRow ->
                            eachRow.containsField(queryKey) && eachRow.getField(queryKey) == queryValue
                        }
                    }
                }
                key == "PARAMETERS.PATH" -> {
                    scenario.httpRequestPattern.httpPathPattern?.pathParameters()?.map { it -> it.key }
                        ?.contains(eachValue) ?: false
                }
                key == "REQUEST-BODY.CONTENT-TYPE" -> {
                    try {
                        MimeType(scenario.httpRequestPattern.headersPattern.contentType).match(MimeType(eachValue))
                    } catch (_: Exception) {
                        false
                    }
                }
                key == "RESPONSE.CONTENT-TYPE" -> {
                    try {
                        MimeType(scenario.httpResponsePattern.headersPattern.contentType).match(MimeType(eachValue))
                    } catch (_: Exception) {
                        false
                    }
                }
                key == "METHOD" -> {
                    scenario.method.equals(eachValue, ignoreCase = true)
                }
                key == "STATUS" -> {
                    scenario.status == eachValue.toIntOrNull()
                }
                key == "EXAMPLE-NAME" -> {
                    scenario.examples.any { example ->
                        example.rows.any { eachRow ->
                            eachRow.name == eachValue
                        }
                    }
                }
                key == "TAGS" -> {
                    scenario.operationMetadata?.tags?.contains(eachValue) ?: false
                }
                key == "SUMMARY" -> {
                    scenario.operationMetadata?.summary.equals(eachValue, ignoreCase = true)
                }
                key == "OPERATION_ID" -> {
                    scenario.operationMetadata?.operationId == eachValue
                }
                key == "DESCRIPTION" -> {
                    scenario.operationMetadata?.description.equals(eachValue, ignoreCase = true)
                }
                else -> {
                    throw IllegalArgumentException("Unknown filter parameter name: $key")
                }
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
