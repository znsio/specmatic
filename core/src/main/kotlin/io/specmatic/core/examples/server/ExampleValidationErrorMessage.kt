package io.specmatic.core.examples.server

private const val JSON_PATH = "jsonPath"
private const val DESCRIPTION = "description"
private const val BREADCRUMB_PREFIX = ">>"
private const val BREADCRUMB_PREFIX_WITH_TRAILING_SPACE = "$BREADCRUMB_PREFIX "
private const val BREADCRUMB_DELIMITER = "."
private const val JSONPATH_DELIMITER = "/"
private const val HTTP_RESPONSE = "http-response"
private const val HTTP_REQUEST = "http-request"
private const val BREAD_CRUMB_HEADERS = "HEADERS"
private const val HTTP_HEADERS = "headers"
const val BREADCRUMB_QUERY_PARAMS = "QUERY-PARAMS"
private const val HTTP_QUERY_PARAMS = "query"
private val BREADCRUMB_WHEN_PATTERN = Regex("\\s\\([\\w\\s]+\\)")

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
            it.removePrefix(BREADCRUMB_PREFIX_WITH_TRAILING_SPACE).replace(BREADCRUMB_WHEN_PATTERN, "").trim()
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