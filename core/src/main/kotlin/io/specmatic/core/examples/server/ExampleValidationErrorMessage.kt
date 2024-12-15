package io.specmatic.core.examples.server

private const val JSON_PATH = "jsonPath"
private const val JSONPATH_DELIMITER = "/"
private const val DESCRIPTION = "description"

private const val BREADCRUMB_PREFIX = ">>"
private const val BREADCRUMB_REQUEST = "REQUEST"
private const val BREADCRUMB_RESPONSE = "RESPONSE"
private const val BREADCRUMB_BODY = "BODY"
private const val BREADCRUMB_PREFIX_WITH_TRAILING_SPACE = "$BREADCRUMB_PREFIX "
private const val BREADCRUMB_DELIMITER = "."

private const val HTTP_RESPONSE = "http-response"
private const val HTTP_REQUEST = "http-request"
private const val HTTP_BODY = "body"

data class ExampleValidationErrorMessage(val fullErrorMessageString: String) {
    fun jsonPathToErrorDescriptionMapping(): List<Map<String, String>> {
        val jsonPaths = jsonPathsForAllErrors(fullErrorMessageString)
        val descriptions = extractDescriptions(fullErrorMessageString)
        val map: List<Map<String, String>> = jsonPaths.zip(descriptions) { jsonPath, description ->
            mapOf(JSON_PATH to jsonPath, DESCRIPTION to description)
        }
        return map
    }

    private fun jsonPathsForAllErrors(errorMessage: String): List<String> {
        val breadcrumbs = errorMessage.lines().map { it.trim() }.filter { it.startsWith(BREADCRUMB_PREFIX_WITH_TRAILING_SPACE) }.map {
            it.removePrefix(
                BREADCRUMB_PREFIX_WITH_TRAILING_SPACE
            )
        }
        return breadcrumbs.map { breadcrumb ->
            breadcrumb
                .replace(BREADCRUMB_RESPONSE, HTTP_RESPONSE)
                .replace(BREADCRUMB_REQUEST, HTTP_REQUEST)
                .replace(BREADCRUMB_BODY, HTTP_BODY)
                .replace(BREADCRUMB_DELIMITER, JSONPATH_DELIMITER)
                .replace(Regex("\\[(\\d+)]")) { matchResult -> "/${matchResult.groupValues[1]}" }
                .let { if (it.startsWith(HTTP_RESPONSE) || it.startsWith(HTTP_REQUEST)) "/$it" else it }
        }
    }

    private fun extractDescriptions(reportString: String): List<String> {
        val parts = reportString.split(BREADCRUMB_PREFIX)

        return parts.drop(1)
            .map { "$BREADCRUMB_PREFIX$it".trim() }
            .filter { it.isNotBlank() }
    }
}