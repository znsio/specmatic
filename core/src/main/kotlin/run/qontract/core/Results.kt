package run.qontract.core

data class Results(val results: MutableList<Result> = mutableListOf()) {
    fun hasFailures(): Boolean = results.any { it is Result.Failure }
    fun hasSuccess(): Boolean = results.any { it is Result.Success }
    fun success(): Boolean = hasSuccess() && !hasFailures()

    fun withoutFluff(): Results = copy(results = results.filterNot { isFluff(it) }.toMutableList())

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

        val defaultHeaders = mapOf("Content-Type" to "text/plain", "X-Qontract-Result" to "failure")
        val headers = when {
            report.isEmpty() -> defaultHeaders.plus("X-Qontract-Empty" to "true")
            else -> defaultHeaders
        }

        return HttpResponse(400, report(), headers)
    }

    fun report(defaultMessage: String = "Match not found"): String {
        val filteredResults = results.filterNot { isFluff(it) }

        return when {
            filteredResults.isNotEmpty() -> listToReport(filteredResults)
            else -> "$defaultMessage\n\n${listToReport(results)}".trim()
        }
    }

    fun nonStrictSuccess(): Boolean {
        return results.any {
            it is Result.Failure && shouldFail(it)
        }
    }
}

internal fun isFluff(it: Result?): Boolean {
    return when(it) {
        is Result.Failure -> it.fluff || isFluff(it.cause)
        else -> false
    }
}

private fun listToReport(list: List<Result>): String = list.map { result ->
    resultReport(result)
}.filter { it.isNotBlank() }.joinToString("\n\n")
