package `in`.specmatic.mock

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.Results

class NoMatchingScenario(val results: Results, val msg: String = "No match was found.") : Exception(msg) {
    fun report(request: HttpRequest): String {
        return if(results.hasResults())
            "${System.lineSeparator()}${System.lineSeparator()}${results.report(request)}"
        else
            msg
    }
}
