package run.qontract.core

import java.util.*

sealed class Result {
    abstract fun record(executionInfo: ExecutionInfo, request: HttpRequest, response: HttpResponse?)

    lateinit var scenario: Scenario

    fun updateScenario(scenario: Scenario) {
        this.scenario = scenario
    }

    abstract fun toBoolean(): Boolean

    class Success : Result() {
        override fun record(executionInfo: ExecutionInfo, request: HttpRequest, response: HttpResponse?) {
            executionInfo.recordSuccessfulInteraction()
        }

        override fun toBoolean() = true
    }

    class Failure : Result {
        private var cause: Failure? = null
        private val errorMessage: String

        constructor(errorMessage: String) : super() {
            this.errorMessage = errorMessage
        }

        private constructor(errorMessage: String, cause: Failure) : super() {
            this.errorMessage = errorMessage
            this.cause = cause
        }

        fun add(errorMessage: String) = Failure(errorMessage, this)

        fun stackTrace(): Stack<String> {
            val stackTrace = Stack<String>()
            this.addTo(stackTrace)
            return stackTrace
        }

        private fun addTo(stackTrace: Stack<String>) {
            cause.let {
                it?.addTo(stackTrace)
            }
            stackTrace.push(this.errorMessage)
        }

        override fun record(executionInfo: ExecutionInfo, request: HttpRequest, response: HttpResponse?) {
            executionInfo.recordUnsuccessfulInteraction(this.scenario, this.stackTrace(), request, response)
        }

        override fun toBoolean() = false
    }
}