package run.qontract.core

const val PATH_NOT_RECOGNIZED_ERROR = "URL path not recognised"

data class Results(val results: MutableList<Result> = mutableListOf()) {
    fun hasFailures(): Boolean = results.any { it is Result.Failure }
    fun success(): Boolean = !hasFailures()

    fun withoutFluff(): Results = copy(results = results.filterNot { isURLPathMismatch(it) }.toMutableList())

    fun toResultIfAny(): Result {
        return results.find { it is Result.Success } ?: Result.Failure(results.joinToString("\n\n") { resultReport(it) })
    }

    val failureCount
        get(): Int = results.count { it is Result.Failure }

    val successCount
        get(): Int = results.count { it is Result.Success }

    fun add(result: Result) {
        results.add(result)
    }

    fun generateErrorHttpResponse(): HttpResponse {
        val report = report("").trim()

        val defaultHeaders = mapOf("Content-Type" to "text/plain", QONTRACT_RESULT_HEADER to "failure")
        val headers = when {
            report.isEmpty() -> defaultHeaders.plus("X-Qontract-Empty" to "true")
            else -> defaultHeaders
        }

        return HttpResponse(400, report(), headers)
    }

    fun report(defaultMessage: String = PATH_NOT_RECOGNIZED_ERROR): String {
        val filteredResults = results.filterNot { isURLPathMismatch(it) }

        return when {
            filteredResults.isNotEmpty() -> listToReport(filteredResults)
            else -> "$defaultMessage\n\n${listToReport(results)}".trim()
        }
    }

}

internal fun isURLPathMismatch(it: Result?): Boolean {
    return when(it) {
        is Result.Failure -> it.failureReason == FailureReason.URLPathMisMatch || isURLPathMismatch(it.cause)
        else -> false
    }
}

private fun listToReport(list: List<Result>): String = list.map { result ->
    resultReport(result)
}.filter { it.isNotBlank() }.joinToString("\n\n")
