package run.qontract.core

data class Results(val results: MutableList<Triple<Result, HttpRequest, HttpResponse?>> = mutableListOf()) {
    fun hasFailures(): Boolean = results.any { it.first is Result.Failure }
    fun hasSuccess(): Boolean = results.any { it.first is Result.Success }
    fun success(): Boolean = hasSuccess() && !hasFailures()

    val failureCount
        get(): Int = results.count { it.first is Result.Failure }

    val successCount
        get(): Int = results.count { it.first is Result.Success }

    fun add(result: Result, httpRequest: HttpRequest, httpResponse: HttpResponse?) {
        results.add(Triple(result, httpRequest, httpResponse))
    }

    fun generateErrorHttpResponse() =
            HttpResponse(400, generateErrorResponseBody(), mutableMapOf("Content-Type" to "text/plain"))

    private fun generateErrorResponseBody() =
            generateFeedback()

    private fun generateFeedback(): String {
        return results.joinToString("\n\n") { (result, request, response) ->
            resultReport(result, request, response)
        }
    }

    fun report() = generateErrorResponseBody()
}

fun resultReport(result: Result, request: HttpRequest, response: HttpResponse?): String {
    return resultReport(result)
}

fun resultReport(result: Result): String {
    val firstLine = when(val scenario = result.scenario) {
        null -> ""
        else -> {
            """In scenario "${scenario.name}""""
        }
    }

    val report = if (result is Result.Failure) {
        result.report().let { (breadCrumbs, errorMessages) ->
            val breadCrumbString = breadCrumbs.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(".")
            val errorMessagesString = errorMessages.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            ">> $breadCrumbString\n\n$errorMessagesString".trim()
        }
    } else ""

    return "$firstLine\n$report".trim()
}
