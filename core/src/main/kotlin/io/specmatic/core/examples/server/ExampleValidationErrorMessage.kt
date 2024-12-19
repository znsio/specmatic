package io.specmatic.core.examples.server

import io.specmatic.core.MatchFailureDetails

private const val JSON_PATH = "jsonPath"
private const val DESCRIPTION = "description"
private const val SEVERITY = "severity"
private const val BREADCRUMB_DELIMITER = "."
private const val JSONPATH_DELIMITER = "/"
private const val HTTP_RESPONSE = "http-response"
private const val HTTP_REQUEST = "http-request"
private const val BREADCRUMB_PREFIX = ">>"
private const val BREADCRUMB_PREFIX_WITH_TRAILING_SPACE = ">> "
private const val BREAD_CRUMB_HEADERS = "HEADERS"
private const val HTTP_HEADERS = "headers"
const val BREADCRUMB_QUERY_PARAMS = "QUERY-PARAMS"
private const val HTTP_QUERY_PARAMS = "query"

data class ExampleValidationErrorMessage(val failureDetails: List<MatchFailureDetails>, val reportString: String) {
    fun jsonPathToErrorDescriptionMapping(): List<Map<String, Any>> {
        val jsonPaths = jsonPathsForAllErrors(reportString)
        val descriptions = extractDescriptions(reportString)
        return failureDetails.flatMap { failureDetail ->
            jsonPaths.zip(descriptions).map { (jsonPath, description) ->
                mapOf(
                    "jsonPath" to jsonPath,
                    "description" to description,
                    "severity" to if (failureDetail.isPartial) "warning" else "error"
                )
            }
            }
        }


    private fun jsonPathsForAllErrors(errorMessage: String): List<String> {
        val breadcrumbs = errorMessage.lines().map { it.trim() }.filter { it.startsWith(BREADCRUMB_PREFIX_WITH_TRAILING_SPACE) }.map {
            it.removePrefix(
                BREADCRUMB_PREFIX_WITH_TRAILING_SPACE
            )
        }
        return breadcrumbs.map { breadcrumb ->
            breadcrumb
                .replace("RESPONSE", HTTP_RESPONSE)
                .replace("REQUEST", HTTP_REQUEST)
                .replace("BODY", "body")
                .replace(BREAD_CRUMB_HEADERS, HTTP_HEADERS)
                .replace(BREADCRUMB_QUERY_PARAMS, HTTP_QUERY_PARAMS)
                .replace(BREADCRUMB_DELIMITER, JSONPATH_DELIMITER)
                .replace(Regex("\\[(\\d+)]")) { matchResult -> "/${matchResult.groupValues[1]}" }
                .let { "/${it.trimStart('/')}" }
        }
    }

    private fun extractDescriptions(reportString: String): List<String> {
        val parts = reportString.split(BREADCRUMB_PREFIX)

        return parts.drop(1)
            .map { "$BREADCRUMB_PREFIX$it".trim() }
            .filter { it.isNotBlank() }
    }
}