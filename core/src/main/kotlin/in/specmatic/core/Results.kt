package `in`.specmatic.core

import `in`.specmatic.core.FailureReason.SOAPActionMismatch
import `in`.specmatic.core.FailureReason.URLPathMisMatch

fun pathNotRecognizedMessage(httpRequest: HttpRequest): String {
    val soapActionHeader = "SOAPAction"
    if(httpRequest.headers.containsKey(soapActionHeader))
        return "SOAP request not recognized; path=" + httpRequest.path + ", SOAPAction=${httpRequest.headers.getValue(soapActionHeader)}"

    return "Request not recognized; method=${httpRequest.method}, path=${httpRequest.path}"
}

const val PATH_NOT_RECOGNIZED_ERROR = "URL path or SOAPAction not recognised"

data class Results(val results: List<Result> = emptyList()) {
    fun hasFailures(): Boolean = results.any { it is Result.Failure }
    fun success(): Boolean = !hasFailures()

    fun withoutFluff(): Results = copy(results = results.filterNot { isFluffyError(it) }.toMutableList())

    fun toResultIfAny(): Result {
        return results.find { it is Result.Success } ?: Result.Failure(results.joinToString("\n\n") { resultReport(it) })
    }

    val failureCount
        get(): Int = results.count { it is Result.Failure }

    val successCount
        get(): Int = results.count { it is Result.Success }

    fun generateErrorHttpResponse(): HttpResponse {
        val report = report("").trim()

        val defaultHeaders = mapOf("Content-Type" to "text/plain", SPECMATIC_RESULT_HEADER to "failure")
        val headers = when {
            report.isEmpty() -> defaultHeaders.plus(SPECMATIC_EMPTY_HEADER to "true")
            else -> defaultHeaders
        }

        return HttpResponse(400, report(PATH_NOT_RECOGNIZED_ERROR), headers)
    }

    fun report(httpRequest: HttpRequest): String {
        return report(pathNotRecognizedMessage(httpRequest))
    }

    fun report(defaultMessage: String = PATH_NOT_RECOGNIZED_ERROR): String {
        val filteredResults = results.filterNot { isFluffyError(it) }

        return when {
            filteredResults.isNotEmpty() -> listToReport(filteredResults)
            else -> "$defaultMessage\n\n${listToReport(results)}".trim()
        }
    }

}

internal fun isFluffyError(it: Result?): Boolean {
    return when(it) {
        is Result.Failure ->
            it.failureReason == URLPathMisMatch
                    || it.failureReason == SOAPActionMismatch
                    || isFluffyError(it.cause)
        else -> false
    }
}

private fun listToReport(list: List<Result>): String = list.map { result ->
    resultReport(result)
}.filter { it.isNotBlank() }.joinToString("\n\n")
