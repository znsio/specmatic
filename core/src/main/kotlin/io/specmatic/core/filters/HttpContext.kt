package io.specmatic.core.filters

import io.specmatic.core.Scenario
import java.util.regex.Pattern
import javax.activation.MimeType


class HttpFilterContext(private val scenario: Scenario) : FilterContext {
    override fun includes(key: String, values: List<String>): Boolean {
        return values.any { eachValue ->
            when (key) {
                "PATH" -> {
                    eachValue == scenario.path || matchesPath(eachValue, scenario.path)
                }

                "PARAMETERS.HEADER" -> {
                    scenario.httpRequestPattern.getHeaderKeys().caseInsensitiveContains(eachValue)
                }

                "PARAMETERS.QUERY" -> {
                    scenario.httpRequestPattern.getQueryParamKeys().contains(eachValue)
                }

                "PARAMETERS.PATH" -> {
                    scenario.httpRequestPattern?.httpPathPattern?.pathParameters()?.map{ it -> it.key }?.contains(eachValue) ?: false
                }

                "REQUEST-BODY.CONTENT-TYPE" -> {
                    try {
                        MimeType(scenario.httpRequestPattern.headersPattern.contentType).match(MimeType(eachValue))
                    } catch(_: Exception) { false }
                }

                "RESPONSE.CONTENT-TYPE" -> {
                    try {
                        MimeType(scenario.httpResponsePattern.headersPattern.contentType).match(MimeType(eachValue))
                    } catch(_: Exception) { false }
                }

                "METHOD" -> {
                    scenario.method.equals(eachValue, ignoreCase = true)
                }

                "STATUS" -> {
                    scenario.status == eachValue.toIntOrNull()
                }

                "EXAMPLE-NAME" -> {
                    scenario.examples.any { example ->
                        example.rows.any { eachRow ->
                            eachRow.name == eachValue
                        }
                    }
                }

                "TAGS" -> {
                    scenario.operationMetadata?.tags?.contains(eachValue) ?: false
                }

                "SUMMARY" -> {
                    scenario.operationMetadata?.summary.equals(eachValue, ignoreCase = true)
                }

                "OPERATION_ID" -> {
                    scenario.operationMetadata?.operationId == eachValue
                }

                "DESCRIPTION" -> {
                    scenario.operationMetadata?.description.equals(eachValue, ignoreCase = true)
                }

                else -> {
                    throw IllegalArgumentException("Unknown parameter name: $key")
                }
            }
        }
    }

    override fun compare(filterKey: String, operator: String, filterValue: String): Boolean {
        if (filterKey.uppercase() == "STATUS") {
            return evaluateCondition(operator, filterValue.toIntOrNull() ?: 0, scenario.status)
        } else {
            throw IllegalArgumentException("Unknown filter key: $filterKey")
        }
    }

    private fun evaluateCondition(operator: String, value: Int, scenarioValue: Int): Boolean {
        return when (operator) {
            ">" -> scenarioValue > value
            "<" -> scenarioValue < value
            ">=" -> scenarioValue >= value
            "<=" -> scenarioValue <= value
            else -> throw IllegalArgumentException("Unsupported operator: $operator")
        }
    }

    private fun matchesPath(value: String, scenarioValue: String): Boolean {
        return value.contains("*") &&
                Pattern.compile(
                    value.replace("(", "\\(").replace(")", "\\)").replace("*", ".*")
                ).matcher(scenarioValue).matches()
    }

    // TODO: figure this out
    private fun matchMultipleExpressions(value: String, scenarioValue: String): Boolean {
        val matchValue = scenarioValue.split(",").map { it.trim().removeSuffix("?") }
        return matchValue.any { it == value }
    }
}

private fun Iterable<String>.caseInsensitiveContains(needle: String): Boolean =
    this.any { haystack -> haystack.lowercase().trim().removeSuffix("?") == needle.lowercase() }
