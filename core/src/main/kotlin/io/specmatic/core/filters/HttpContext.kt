package io.specmatic.core.filters

import io.specmatic.core.Scenario
import java.util.regex.Pattern
import javax.activation.MimeType

//"PARAMETERS.QUERY='field1'"
//"PARAMETERS.QUERY.field1='skip'"

// TODO: where does this match?
//"PARAMETERS.PATH='request-id'"
//"PARAMETERS.COOKIE='request-id'"

//"PARAMETERS.HEADER='content-type'" //key
//"PARAMETERS.HEADER.CONTENT-TYPE='text/xml'" //specific value for the key
//"PARAMETERS.HEADER.CONTENT-TYPE='application/*+json'" //specific value for the key

// this if for a post request
//"REQUEST-BODY.CONTENT-TYPE='text/xml'"

// TODO - finalize one of these keys. they mean the same thing?
//"REQUEST.HEADERS.ACCEPT='text/xml'" //key
//"RESPONSE-BODY.CONTENT-TYPE='text/xml'" //key

//"STATUS>'409' && STATUS<'420'"
//"STATUS>'399' && STATUS<'500'"
//"PATH='/user/v*/invoice'"
//"PATH='/user/*/invoice'"
//"PATH='/user/*/invoice' && PARAMETERS.PATH.user_id='naresh'"
//"METHOD='POST'"
//"EXAMPLE-NAME='VALID_PRODUCT_CREATE_201'"

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

                "REQUEST-BODY.CONTENT-TYPE" -> {
                    scenario.httpRequestPattern.headersPattern.contentType
                        ?: throw IllegalArgumentException("Content type is not set for scenario - ${scenario.apiDescription}")
                    MimeType(scenario.httpRequestPattern.headersPattern.contentType).match(eachValue)
                }

                "RESPONSE.CONTENT-TYPE" -> {
                    scenario.httpResponsePattern.headersPattern.contentType
                        ?: throw IllegalArgumentException("Content type is not set for scenario - ${scenario.apiDescription}")

                    MimeType(scenario.httpResponsePattern.headersPattern.contentType).match(eachValue)
                }

                "PARAMETERS.QUERY" -> {
                    scenario.httpRequestPattern.getQueryParamKeys().contains(eachValue)
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
