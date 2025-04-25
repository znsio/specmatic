package io.specmatic.core.examples.module

import io.specmatic.core.Result

class ValidationResults(val exampleValidationResults: Map<String, Result>, private val hookValidationResult: Result) {
    val exitCode: Int
        get() {
            if(exampleValidationResults.containsOnlyCompleteFailures())
                return FAILURE_EXIT_CODE

            if(hookValidationResult is Result.Failure && !hookValidationResult.isPartialFailure())
                return FAILURE_EXIT_CODE

            return SUCCESS_EXIT_CODE
        }

    val errorMessage: String?
        get() {
            TODO()
        }

    fun exitCode(): Int = when (exampleValidationResults.containsOnlyCompleteFailures()) {
        true -> FAILURE_EXIT_CODE
        false -> exitCodeBasedOnHookResult()
    }

    fun exitCodeBasedOnHookResult() = when (hookValidationResult.isSuccess()) {
        true -> SUCCESS_EXIT_CODE
        false -> FAILURE_EXIT_CODE
    }

    private fun Map<String, Result>.containsOnlyCompleteFailures(): Boolean {
        return this.any { it.value is Result.Failure && !it.value.isPartialFailure() }
    }

    companion object {
        fun forNoExamples(): ValidationResults {
            return ValidationResults(emptyMap(), Result.Success())
        }
    }
}

fun List<ValidationResults>.ofAllExamples() =
    flatMap { it.exampleValidationResults.entries }.associate { entry -> entry.toPair() }

fun List<ValidationResults>.exitCode() = when(any { it.exitCode() == FAILURE_EXIT_CODE }) {
    true -> FAILURE_EXIT_CODE
    else -> SUCCESS_EXIT_CODE
}
