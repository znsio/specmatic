package run.qontract.core

fun resultListToResults(results: List<Result>): Results {
    val resultList: MutableList<Triple<Result, HttpRequest?, HttpResponse?>> = results.map { Triple(it, null, null) }.toMutableList()
    return Results(resultList)
}

data class Results(val results: MutableList<Triple<Result, HttpRequest?, HttpResponse?>> = mutableListOf()) {
    fun hasFailures(): Boolean = results.any { it.first is Result.Failure }
    fun hasSuccess(): Boolean = results.any { it.first is Result.Success }
    fun success(): Boolean = hasSuccess() && !hasFailures()

    fun toResultIfAny(): Result {
        return results.find { it.first is Result.Success }?.first ?: Result.Failure(results.joinToString("\n\n") { resultReport(it.first) })
    }

    val failureCount
        get(): Int = results.count { it.first is Result.Failure }

    val successCount
        get(): Int = results.count { it.first is Result.Success }

    fun add(result: Result, httpRequest: HttpRequest, httpResponse: HttpResponse?) {
        results.add(Triple(result, httpRequest, httpResponse))
    }

    fun generateErrorHttpResponse() =
            HttpResponse(400, generateErrorResponseBody(), mutableMapOf("Content-Type" to "text/plain", "X-Qontract-Result" to "failure"))

    private fun generateErrorResponseBody() =
            generateFeedback()

    private fun generateFeedback(): String {
        return results.map { it.first }.map { result ->
            resultReport(result)
        }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun report() = generateErrorResponseBody()
}
