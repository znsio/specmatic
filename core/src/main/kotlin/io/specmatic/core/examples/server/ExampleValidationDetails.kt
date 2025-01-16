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
private const val BREADCRUMB_QUERY_PARAMS = "QUERY-PARAMS"
private const val HTTP_QUERY_PARAMS = "query"

data class ExampleValidationDetails(val matchFailureDetailsList: List<MatchFailureDetails>) {

    fun jsonPathToErrorDescriptionMapping(): List<ExampleValidationResult> {
        if (matchFailureDetailsList.isEmpty()) return emptyList()

        return matchFailureDetailsList.sortedBy { it.isPartial }.map {
            val jsonPaths = processBreadcrumbs(it.breadCrumbs)
            ExampleValidationResult(
                jsonPath = jsonPaths.withoutTildeBreadCrumb().joinToString(JSONPATH_DELIMITER, prefix = JSONPATH_DELIMITER),
                description = processDescription(it.errorMessages, jsonPaths.jsonPathForDescription()),
                severity = extractSeverity(it)
            )
        }
    }

    private fun processBreadcrumbs(breadcrumbs: List<String>): List<String> {
        return breadcrumbs.filter { it.isNotBlank() }.map {
            it.replace(BREADCRUMB_RESPONSE, HTTP_RESPONSE)
            .replace(BREADCRUMB_REQUEST, HTTP_REQUEST)
            .replace(BREADCRUMB_BODY, HTTP_BODY)
            .replace(BREAD_CRUMB_HEADERS, HTTP_HEADERS)
            .replace(BREADCRUMB_QUERY_PARAMS, HTTP_QUERY_PARAMS)
            .replace(BREADCRUMB_DELIMITER, JSONPATH_DELIMITER)
            .replace(Regex("\\[(\\d+)]")) { matchResult -> matchResult.groupValues[1] }
        }
    }

    private fun processDescription(errorMessages: List<String>, breadCrumbs: String): String {
        val errorMessage = errorMessages.joinToString("\n")
        if (breadCrumbs.isEmpty()) return errorMessage
        return ">> ${breadCrumbs}\n\n$errorMessage"
    }

    private fun extractSeverity(matchFailureDetails: MatchFailureDetails): Severity {
        return if (matchFailureDetails.isPartial) {
            Severity.WARNING
        } else Severity.ERROR
    }

    private fun List<String>.withoutTildeBreadCrumb(): List<String> {
        return this.filterNot { it.isTildeBreadCrumb() }
    }

    private fun List<String>.jsonPathForDescription(): String {
        return this.joinToString("") {
            when {
                it.isTildeBreadCrumb() -> it.removePrefix(JSONPATH_DELIMITER).replace(Regex("\\.?\\(~{3}"), " (when")
                it.all(Char::isDigit) -> ".[$it]"
                else -> ".$it"
            }
        }.removePrefix(".")
    }

    private fun String.isTildeBreadCrumb(): Boolean  {
        return this.removePrefix(JSONPATH_DELIMITER).startsWith("(") && this.endsWith(")")
    }
}