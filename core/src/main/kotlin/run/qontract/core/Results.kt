package run.qontract.core

data class Results(val results: MutableList<Result> = mutableListOf()) {
    fun hasFailures(): Boolean = results.any { it is Result.Failure }
    fun hasSuccess(): Boolean = results.any { it is Result.Success }
    fun success(): Boolean = hasSuccess() && !hasFailures()

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

    fun generateErrorHttpResponse() =
            HttpResponse(400, generateErrorResponseBody(), mutableMapOf("Content-Type" to "text/plain", "X-Qontract-Result" to "failure"))

    private fun generateErrorResponseBody() =
            generateFeedback()

    private fun generateFeedback(): String {
        return results.map { it }.map { result ->
            resultReport(result)
        }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun report() = generateErrorResponseBody()
}
