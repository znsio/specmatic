package run.qontract.core

import java.util.*
import kotlin.collections.HashMap

class ExecutionInfo(results: List<Triple<Result, HttpRequest?, HttpResponse?>> = emptyList()) {
    private val mismatchInformation: HashMap<Scenario, Stack<String>> = HashMap()
    private val erroneousRequestResponse: HashMap<Scenario, Pair<HttpRequest, HttpResponse?>> = HashMap()
    private val errorInformation: HashMap<Scenario, Throwable> = HashMap()
    private var success: Int = 0

    private fun generateFeedback() : String {
        val message = StringBuilder()
        mismatchInformation.map { (scenario, stackTrace) ->
           message.appendln("$scenario Error:")
           while(stackTrace.isNotEmpty()) {
               message.appendln("\t${stackTrace.pop()}")
           }
            erroneousRequestResponse[scenario]?.let { requestAndResponse ->
                val (request, response) = requestAndResponse
                message.appendln("\tRequest: $request")
                response?.let {
                    message.appendln("\tResponse: $it")
                }
            }
        }

        if(errorInformation.isNotEmpty()) {
            message.append("\n\nExceptions thrown:\n")

            for((scenario, exception) in errorInformation) {
                message.append("${scenario.name}\n${exception.javaClass}: $exception\n\n")
            }
        }

        return message.toString()
    }

    val successCount
        get() = success

    val failureCount
        get() = mismatchInformation.size + errorInformation.size

    val hasErrors
        get() = mismatchInformation.size > 0 || errorInformation.size > 0

    private fun add(scenario: Scenario, stackTrace: Stack<String>) {
        mismatchInformation[scenario] = stackTrace
    }

    fun recordSuccessfulInteraction() {
        success++
    }

    fun recordUnsuccessfulInteraction(scenario: Scenario, error: Stack<String>, request: HttpRequest, response: HttpResponse?) {
        add(scenario, error)
        erroneousRequestResponse[scenario] = request to response
    }

    fun recordInteractionError(scenario: Scenario, exception: Throwable) {
        errorInformation[scenario] = exception
    }

    fun generateErrorMessage() = generateErrorResponseBody()

    private fun generateErrorResponseBody() =
            "This request did not match any scenario.\n".plus(generateFeedback())

    fun print() {
        val totalInteractionsExcuted = success + mismatchInformation.size + errorInformation.size
        println("Tests run: ${totalInteractionsExcuted}, Failures: ${mismatchInformation.size}, Errors: ${errorInformation.size}")

        val feedback = generateFeedback()

        if(feedback.isNotEmpty()) {
            println("Failed Interactions")
            println("===================")
            println(feedback)
        }
    }

    fun unsuccessfulInteractionCount(): Int {
        return mismatchInformation.size
    }

    init {
        results.forEach { (result, request, response) ->
//            when(result) {
//                is Result.Failure -> this.recordUnsuccessfulInteraction(result.scenario ?: Scenario("test"), result.stackTrace(), request!!, response!!)
//                else -> this.recordSuccessfulInteraction()
//            }
        }
    }
}
