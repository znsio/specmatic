package `in`.specmatic.core

fun requestNotRecognized(httpRequest: HttpRequest): String {
    val soapActionHeader = "SOAPAction"
    if(httpRequest.headers.containsKey(soapActionHeader))
        return "No matching contract found (SOAPAction ${httpRequest.headers.getValue(soapActionHeader)}, path ${httpRequest.path})"

    return "No matching contract found"
}

const val PATH_NOT_RECOGNIZED_ERROR = "URL path or SOAPAction not recognised"

data class Results(val results: List<Result> = emptyList()) {
    fun hasResults(): Boolean = results.isNotEmpty()

    fun hasFailures(): Boolean = results.any { it is Result.Failure }
    fun success(): Boolean = !hasFailures()

    fun withoutFluff(): Results = copy(results = results.filterNot { it.isFluffy() })

    fun toResultIfAny(): Result {
        return results.find { it is Result.Success } ?: Result.Failure(results.joinToString("\n\n") { it.toReport().toText() })
    }

    val failureCount
        get(): Int = results.count { it is Result.Failure }

    val successCount
        get(): Int = results.count { it is Result.Success }

    fun generateErrorHttpResponse(httpRequest: HttpRequest? = null): HttpResponse {
        val report = report("").trim()

        val defaultHeaders = mapOf("Content-Type" to "text/plain", SPECMATIC_RESULT_HEADER to "failure")
        val headers = when {
            report.isEmpty() -> defaultHeaders.plus(SPECMATIC_EMPTY_HEADER to "true")
            else -> defaultHeaders
        }

        val message = httpRequest?.let { requestNotRecognized(httpRequest) } ?: PATH_NOT_RECOGNIZED_ERROR
        return HttpResponse(400, report(message), headers)
    }

    fun report(httpRequest: HttpRequest): String {
        return report(requestNotRecognized(httpRequest))
    }

    fun report(defaultMessage: String = PATH_NOT_RECOGNIZED_ERROR): String {
        val filteredResults = withoutFluff().results

        return when {
            filteredResults.isNotEmpty() -> listToReport(filteredResults)
            else -> "$defaultMessage\n\n${listToReport(results)}".trim()
        }
    }

    fun plus(other: Results): Results = Results(results.plus(other.results))
}

private fun listToReport(results: List<Result>): String =
    results.filterIsInstance<Result.Failure>().joinToString("${System.lineSeparator()}${System.lineSeparator()}") {
        it.toFailureReport().toText()
    }
