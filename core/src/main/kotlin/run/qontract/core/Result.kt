package run.qontract.core

import run.qontract.core.Result.*
import run.qontract.core.pattern.Pattern
import run.qontract.core.value.Value

sealed class Result {
    var scenario: Scenario? = null

    fun reportString(): String {
        return resultReport(this)
    }

    fun updateScenario(scenario: Scenario): Result {
        this.scenario = scenario
        return this
    }

    abstract fun isTrue(): Boolean

    abstract fun ifSuccess(function: () -> Result): Result

    class Success : Result() {
        override fun isTrue() = true
        override fun ifSuccess(function: () -> Result) = function()
    }

    data class Failure(val message: String="", var cause: Failure? = null, val breadCrumb: String = "", val failureReason: FailureReason? = null) : Result() {
        override fun ifSuccess(function: () -> Result) = this

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

        override fun isTrue() = false
    }

}

enum class FailureReason {
    PartNameMisMatch,
    URLPathMisMatch
}

fun Result.breadCrumb(breadCrumb: String): Result =
    when(this) {
        is Failure -> this.breadCrumb(breadCrumb)
        else -> this
    }

data class FailureReport(val breadCrumbs: List<String> = emptyList(), val errorMessages: List<String> = emptyList())

fun mismatchResult(expected: String, actual: String): Failure = Failure("Expected $expected, actual was $actual")
fun mismatchResult(expected: String, actual: Value?): Failure = mismatchResult(expected, valueError(actual) ?: "null")
fun mismatchResult(expected: Value, actual: Value?): Result = mismatchResult(valueError(expected) ?: "null", valueError(actual) ?: "nothing")
fun mismatchResult(expected: Pattern, actual: String): Failure = mismatchResult(expected.typeName, actual)
fun mismatchResult(pattern: Pattern, sampleData: Value?): Failure = mismatchResult(pattern, sampleData?.toStringValue() ?: "null")
fun mismatchResult(thisPattern: Pattern, otherPattern: Pattern): Failure {
    return mismatchResult(thisPattern.typeName, otherPattern.typeName)
}

fun valueError(value: Value?): String? {
    return value?.let { "${it.displayableType()}: ${it.displayableValue()}" }
}

fun resultReport(result: Result, scenarioMessage: String? = null): String {
    return when (result) {
        is Failure -> {
            val firstLine = when(val scenario = result.scenario) {
                null -> ""
                else -> {
                    """${scenarioMessage ?: "In scenario"} "${scenario.name}""""
                }
            }

            val report = result.report().let { (breadCrumbs, errorMessages) ->
                val breadCrumbString =
                        breadCrumbs
                                .filter { it.isNotBlank() }
                                .joinToString(".") { it.trim() }
                                .let {
                                    when {
                                        it.isNotBlank() -> ">> $it"
                                        else -> ""
                                    }
                                }
                val errorMessagesString = errorMessages.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
                "$breadCrumbString\n\n$errorMessagesString".trim()
            }

            "$firstLine\n$report"
        }
        else -> ""
    }.trim()
}

fun shouldBeIgnored(result: Result): Boolean {
    return when(result) {
        is Success -> false
        is Failure -> result.scenario?.ignoreFailure == true
    }
}