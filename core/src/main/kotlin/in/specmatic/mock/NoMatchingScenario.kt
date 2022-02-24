package `in`.specmatic.mock

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.Results

private const val defaultMessage = "No match was found."

class NoMatchingScenario(val results: Results, val msg: String? = defaultMessage, cachedMessage: String? = null) : Exception(cachedMessage ?: msg) {
    fun withoutFluff(): NoMatchingScenario {
        return NoMatchingScenario(results.withoutFluff(), msg)
    }

    fun report(request: HttpRequest): String {
        return if(results.hasResults()) {
            val messagePrefix = msg?.let { "$msg${System.lineSeparator()}${System.lineSeparator()}" } ?: ""
            "$messagePrefix${results.report(request)}"
        }
        else
            msg ?: defaultMessage
    }
}
