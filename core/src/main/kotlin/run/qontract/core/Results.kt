package run.qontract.core

import java.util.*

class Results {
    val results: LinkedList<Triple<Result, HttpRequest, HttpResponse?>> = LinkedList()

    fun add(result: Result, httpRequest: HttpRequest, httpResponse: HttpResponse?) {
        results.add(Triple(result, httpRequest, httpResponse))
    }

    fun generateErrorHttpResponse() =
            HttpResponse(400, generateErrorResponseBody(), java.util.HashMap())

    private fun generateErrorResponseBody() =
            "This request did not match any scenario.\n".plus(generateFeedback())

    private fun generateFeedback(): String {
        val message = StringBuilder()
        results.map { (result, request, response) ->
            message.appendln("${result.scenario} Error:")
            val stackTrace = (result as Result.Failure).stackTrace()
            while (stackTrace.isNotEmpty()) {
                message.appendln("\t${stackTrace.pop()}")
            }
            message.appendln("\tRequest: $request")
            response?.let {
                message.appendln("\tResponse: $it")
            }
        }
        return message.toString()
    }

    fun generateErrorMessage() = generateErrorResponseBody()
}