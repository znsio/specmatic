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

private const val BREAD_CRUMB_HEADERS = "HEADERS"
private const val HTTP_HEADERS = "headers"
const val BREADCRUMB_QUERY_PARAMS = "QUERY-PARAMS"
private const val HTTP_QUERY_PARAMS = "query"

data class ExampleValidationErrorMessage(val failureDetails: List<MatchFailureDetails>) {
    fun jsonPathToErrorDescriptionMapping(): List<Map<String, Any>> {
        //On Example Load
        //Error Message Ex- Key @type in the specification is missing from the example
        //bread crumb = ArrayList - 1 - REQUEST, 2-BODY, 3-@type

        /* On click of validate
            Error Message-
            In scenario "Create a subscription (hub) to receive Events. Response: Notified"
            API: POST /hub -> 201

                >> REQUEST.BODY.@type

                Key @type in the specification is missing from the example


                >> RESPONSE.BODY.@types

                Key @types in the example is not in the specification

              BreadCrumbs - Empty Array List
         */
        return failureDetails.flatMap { failureDetail ->
            val transformedJsonPaths = failureDetail.transformBreadcrumbs()
            listOf(
                mapOf(
                    JSON_PATH to transformedJsonPaths,
                    DESCRIPTION to failureDetail.errorMessages.joinToString(", "),
                    SEVERITY to if (failureDetail.isPartial) "warning" else "error"
                )
            )
            }
        }


    private fun MatchFailureDetails.transformBreadcrumbs(): List<String> {
         val breadCrumbs = if (this.breadCrumbs.isEmpty()) {
             this.errorMessages
                 .flatMap { message ->
                     message.lines()
                 }
                 .filter { it.trim().startsWith(BREADCRUMB_PREFIX) }
                 .map {
                     it.removePrefix(BREADCRUMB_PREFIX).trim()
                 }
         } else {
             listOf(this.breadCrumbs.joinToString("."))
         }
        return breadCrumbs.map { breadcrumb ->
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