package io.specmatic.core.examples.server

import io.specmatic.core.MatchFailureDetails

private const val JSON_PATH = "jsonPath"
private const val DESCRIPTION = "description"
private const val SEVERITY = "severity"
private const val BREADCRUMB_PREFIX = ">>"
private const val BREADCRUMB_PREFIX_WITH_TRAILING_SPACE = "$BREADCRUMB_PREFIX "
private const val BREADCRUMB_DELIMITER = "."
private const val JSONPATH_DELIMITER = "/"
private const val HTTP_RESPONSE = "http-response"
private const val HTTP_REQUEST = "http-request"

data class ExampleValidationErrorMessage(val failureDetails : List<MatchFailureDetails>) {
    fun jsonPathToErrorDescriptionMapping(): List<Map<String, String>> {
        return failureDetails.flatMap { failureDetail ->
            failureDetail.breadCrumbs.map { breadcrumb ->
                breadcrumb
                    .transformBreadcrumb()
                    .let { jsonPath ->
                        mapOf(
                            JSON_PATH to jsonPath,
                            DESCRIPTION to failureDetail.errorMessages.joinToString(", "),
                            SEVERITY to if (failureDetail.isPartial) "warning" else "error"
                        )
                    }
            }
        }
    }

    private fun String.transformBreadcrumb(): String {
        return this
            .replace("RESPONSE", HTTP_RESPONSE)
            .replace("REQUEST", HTTP_REQUEST)
            .replace("BODY", "body")
            .replace(BREADCRUMB_DELIMITER, JSONPATH_DELIMITER)
            .replace(Regex("\\[(\\d+)]")) { "/${it.groupValues[1]}" }
            .let { if (it.startsWith(HTTP_RESPONSE) || it.startsWith(HTTP_REQUEST)) "/$it" else it }
    }
}