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
private const val BREADCRUMB_PREFIX_WITH_TRAILING_SPACE = "$BREADCRUMB_PREFIX "

data class ExampleValidationErrorMessage(val failureDetails : List<MatchFailureDetails>) {
    fun jsonPathToErrorDescriptionMapping(): List<Map<String, Any>> {
        return failureDetails.flatMap { failureDetail ->
            val transformedJsonPaths = transformBreadcrumbs(failureDetail.errorMessages)
            listOf(
                mapOf(
                    JSON_PATH to transformedJsonPaths,
                    DESCRIPTION to failureDetail.errorMessages.joinToString(", "),
                    SEVERITY to if (failureDetail.isPartial) "warning" else "error"
                )
            )
            }
        }


    private fun transformBreadcrumbs(errorMessages: List<String>): List<String> {
        val breadcrumbs = errorMessages.map { it.trim() }.filter { it.startsWith(BREADCRUMB_PREFIX_WITH_TRAILING_SPACE) }.map {
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
}