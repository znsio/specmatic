package `in`.specmatic.core

import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.value.Value

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
    abstract fun withBindings(bindings: Map<String, String>, response: HttpResponse): Result

    data class Failure(val message: String="", var cause: Failure? = null, val breadCrumb: String = "", val failureReason: FailureReason? = null) : Result() {
        override fun ifSuccess(function: () -> Result) = this
        override fun withBindings(bindings: Map<String, String>, response: HttpResponse): Result {
            return this
        }

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

    data class Success(val variables: Map<String, String> = emptyMap()) : Result() {
        override fun isTrue() = true
        override fun ifSuccess(function: () -> Result) = function()
        override fun withBindings(bindings: Map<String, String>, response: HttpResponse): Result {
            return this.copy(variables = response.export(bindings))
        }
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
        is Result.Success -> false
        is Failure -> result.scenario?.ignoreFailure == true
    }
}