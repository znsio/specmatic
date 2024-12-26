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
private const val IS_PARTIAL = "isPartial"

data class ExampleValidationErrorMessage(val fullErrorMessageString: String, val partialErrorList: List<Boolean>) {
    fun jsonPathToErrorDescriptionMapping(): List<Map<String, Any>> {
        val jsonPaths = jsonPathsForAllErrors(fullErrorMessageString)
        val descriptions = extractDescriptions(fullErrorMessageString)
        val maxSize = listOf(jsonPaths.size, descriptions.size, partialErrorList.size).minOrNull() ?: 0
        return jsonPaths.take(maxSize).zip(descriptions.take(maxSize))
            .zip(partialErrorList.take(maxSize)) { (jsonPath, description), isPartial ->
                mapOf(
                    JSON_PATH to jsonPath,
                    DESCRIPTION to description,
                    IS_PARTIAL to isPartial
                )
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