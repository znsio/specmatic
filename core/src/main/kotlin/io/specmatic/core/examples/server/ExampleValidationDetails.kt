package io.specmatic.core.examples.server

import io.specmatic.core.MatchFailureDetails

private const val BREADCRUMB_RESPONSE = "RESPONSE"
private const val BREADCRUMB_REQUEST = "REQUEST"
private const val BREADCRUMB_BODY = "BODY"
private const val BREADCRUMB_DELIMITER = "."
private const val JSONPATH_DELIMITER = "/"
private const val HTTP_RESPONSE = "http-response"
private const val HTTP_REQUEST = "http-request"
private const val BREAD_CRUMB_HEADERS = "HEADERS"
private const val HTTP_HEADERS = "headers"
private const val HTTP_BODY = "body"
const val BREADCRUMB_QUERY_PARAMS = "QUERY-PARAMS"
private const val HTTP_QUERY_PARAMS = "query"

data class ExampleValidationDetails(val matchFailureDetailsList: List<MatchFailureDetails>) {
    fun jsonPathToErrorDescriptionMapping(): List<ExampleValidationResult> {
        val jsonPaths = jsonPathsForAllErrors(matchFailureDetailsList)
        val descriptions = extractDescriptions(matchFailureDetailsList)
        val severities = extractSeverities(matchFailureDetailsList)
        val maxSize = listOf(jsonPaths.size, descriptions.size, severities.size).minOrNull() ?: 0
        return jsonPaths.take(maxSize).zip(descriptions.take(maxSize))
            .zip(severities.take(maxSize)) { (jsonPath, description), severity ->
                ExampleValidationResult(
                    jsonPath = jsonPath,
                    description = description,
                    severity = severity
                )
            }
    }

    private fun jsonPathsForAllErrors(matchingFailureDetails: List<MatchFailureDetails>): List<String> {
        return matchingFailureDetails.flatMap { matchingFailureDetail ->
            val combinedBreadcrumbs = matchingFailureDetail.breadCrumbs
                .takeIf { it.isNotEmpty() }
                ?.filterNot { it.startsWith("(") && it.endsWith(")") }
                ?.joinToString(JSONPATH_DELIMITER) { breadcrumb -> processBreadcrumb(breadcrumb) }
                ?.let { "/$it" }
                ?: ""
            listOf(combinedBreadcrumbs)
        }
    }

    private fun processBreadcrumb(breadcrumb: String): String {
        return breadcrumb
            .replace(BREADCRUMB_RESPONSE, HTTP_RESPONSE)
            .replace(BREADCRUMB_REQUEST, HTTP_REQUEST)
            .replace(BREADCRUMB_BODY, HTTP_BODY)
            .replace(BREAD_CRUMB_HEADERS, HTTP_HEADERS)
            .replace(BREADCRUMB_QUERY_PARAMS, HTTP_QUERY_PARAMS)
            .replace(BREADCRUMB_DELIMITER, JSONPATH_DELIMITER)
            .replace(Regex("\\[(\\d+)]")) { matchResult -> "/${matchResult.groupValues[1]}" }.trimStart('/')
    }

    private fun extractDescriptions(matchingFailureDetails: List<MatchFailureDetails>): List<String> {
        return matchingFailureDetails.flatMap { matchingFailureDetail ->
            matchingFailureDetail.errorMessages.map { errorMessage ->
                val breadcrumb = matchingFailureDetail.breadCrumbs
                    .joinToString(BREADCRUMB_DELIMITER) { breadcrumbPart ->
                        if (breadcrumbPart.matches(Regex("\\[(\\d+)]"))) {
                            "[${processBreadcrumb(breadcrumbPart)}]"
                        } else {
                            processBreadcrumb(breadcrumbPart)
                        }
                    }
                ">> $breadcrumb\n\n$errorMessage"
            }
        }
    }

    private fun extractSeverities(matchFailureDetailsList: List<MatchFailureDetails>): List<Severity> {
        return matchFailureDetailsList.map { matchFailureDetails ->
            if (matchFailureDetails.isPartial) {
                Severity.WARNING
            } else {
                Severity.ERROR
            }
        }
    }
}