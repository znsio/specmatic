package `in`.specmatic.core

const val PATH_NOT_RECOGNIZED_ERROR = "Match not found"

data class Results(val results: List<Result> = emptyList()) {
    fun hasResults(): Boolean = results.isNotEmpty()

    fun hasFailures(): Boolean = results.any { it is Result.Failure }
    fun success(): Boolean = !hasFailures()

    fun withoutFluff(fluffLevel: Int): Results = copy(results = results.filterNot { it.isFluffy(fluffLevel) })

    fun withoutFluff(): Results = copy(results = minimumFluff())

    private fun minimumFluff() = results.filterNot { it.isFluffy() }.ifEmpty { results.filterNot { it.isFluffy(1) } }

    fun toResultIfAny(): Result {
        return results.find { it is Result.Success } ?: Result.Failure(results.joinToString("\n\n") { it.toReport().toText() })
    }

    val failureCount
        get(): Int = results.count { it is Result.Failure }

    val successCount
        get(): Int = results.count { it is Result.Success }

    fun generateErrorHttpResponse(httpRequest: HttpRequest): HttpResponse {
        val report = report("").trim()

        val defaultHeaders = mapOf("Content-Type" to "text/plain", SPECMATIC_RESULT_HEADER to "failure")
        val headers = when {
            report.isEmpty() -> defaultHeaders.plus(SPECMATIC_EMPTY_HEADER to "true")
            else -> defaultHeaders
        }

        val message = httpRequest.let { httpRequest.requestNotRecognized() }
        return HttpResponse(400, report(message), headers)
    }

    fun report(httpRequest: HttpRequest): String {
        return report(httpRequest.requestNotRecognized())
    }

    fun strictModeReport(httpRequest: HttpRequest): String {
        return report(httpRequest.requestNotRecognizedInStrictMode())
    }

    fun report(defaultMessage: String = PATH_NOT_RECOGNIZED_ERROR): String {
        val filteredResults = withoutFluff().results.filterIsInstance<Result.Failure>()

        return when {
            filteredResults.isNotEmpty() -> listToReport(filteredResults)
            else -> defaultMessage.trim()
        }
    }

    fun distinctReport(defaultMessage: String = PATH_NOT_RECOGNIZED_ERROR): String {
        val filteredResults = withoutFluff().results.filterIsInstance<Result.Failure>()

        return when {
            filteredResults.isNotEmpty() -> listToDistinctReport(filteredResults)
            else -> if(successCount > 0 && failureCount == 0) "" else defaultMessage.trim()
        }
    }

    fun plus(other: Results): Results = Results(results.plus(other.results))

    fun distinct(): Results {
        val filteredResults = withoutFluff().results
        val resultReports = filteredResults.map {
            when(it) {
                is Result.Failure -> it.toFailureReport().toText()
                else -> ""
            }
        }

        val uniqueResults: List<Result> = filteredResults.foldIndexed(emptyList()) { index, acc, result ->
            val report = resultReports[index]
            when(result) {
                is Result.Failure -> {
                    if(resultReports.indexOf(report) == index)
                        acc.plus(result)
                    else
                        acc
                }
                else -> acc.plus(result)
            }
        }

        return Results(uniqueResults)
    }
}

private fun listToReport(results: List<Result>): String {
    return results.filterIsInstance<Result.Failure>()
        .joinToString("${System.lineSeparator()}${System.lineSeparator()}") {
            it.toFailureReport().toText()
        }
}

private fun listToDistinctReport(results: List<Result>): String {
    return results.filterIsInstance<Result.Failure>().map {
        it.toFailureReport().toText()
    }.distinct().joinToString("${System.lineSeparator()}${System.lineSeparator()}")
}
