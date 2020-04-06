package run.qontract.core

import run.qontract.core.pattern.Pattern
import run.qontract.core.value.Value
import java.util.*

sealed class Result {
    abstract fun record(executionInfo: ExecutionInfo, request: HttpRequest, response: HttpResponse?)

    lateinit var scenario: Scenario

    fun updateScenario(scenario: Scenario) {
        this.scenario = scenario
    }

    abstract fun isTrue(): Boolean

    class Success : Result() {
        override fun record(executionInfo: ExecutionInfo, request: HttpRequest, response: HttpResponse?) {
            executionInfo.recordSuccessfulInteraction()
        }

        override fun isTrue() = true
    }

    data class Failure(val message: String="", var cause: Failure? = null, val breadCrumb: String = "") : Result() {
        fun reason(errorMessage: String) = Failure(errorMessage, this)
        fun breadCrumb(breadCrumb: String) = Failure(cause = this, breadCrumb = breadCrumb)

        fun report(): FailureReport =
            (cause?.report() ?: FailureReport()).let { reason ->
                when {
                    message.isNotEmpty() -> reason.copy(errorMessages = listOf(message).plus(reason.errorMessages))
                    else -> reason
                }
            }.let { reason ->
                when {
                    breadCrumb.isNotEmpty() -> reason.copy(breadCrumbs = listOf(breadCrumb).plus(reason.breadCrumbs))
                    else -> reason
                }
            }

        fun stackTrace(): Stack<String> {
            val stackTrace = Stack<String>()
            this.addTo(stackTrace)
            return stackTrace
        }

        private fun addTo(stackTrace: Stack<String>) {
            cause?.addTo(stackTrace)
            stackTrace.push(this.message)
        }

        override fun record(executionInfo: ExecutionInfo, request: HttpRequest, response: HttpResponse?) {
            executionInfo.recordUnsuccessfulInteraction(this.scenario, this.stackTrace(), request, response)
        }

        override fun isTrue() = false
    }
}

data class FailureReport(val breadCrumbs: List<String> = emptyList(), val errorMessages: List<String> = emptyList())

fun mismatchResult(expected: String, actual: String): Result.Failure = Result.Failure("Expected $expected, actual $actual")
fun mismatchResult(expected: String, actual: Value?): Result.Failure = mismatchResult(expected, actual?.toDisplayValue() ?: "null")
fun mismatchResult(expected: Value, actual: Value?): Result = mismatchResult(expected.toDisplayValue(), actual?.toDisplayValue() ?: "")
fun mismatchResult(expected: Pattern, actual: String): Result.Failure = mismatchResult(patternClassNameToString(expected), actual)

fun patternClassNameToString(value: Pattern): String =
        value.javaClass.name.split(".").last().removeSuffix("Pattern").toLowerCase()
