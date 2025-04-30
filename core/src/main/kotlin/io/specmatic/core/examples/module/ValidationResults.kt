package io.specmatic.core.examples.module

import io.specmatic.core.Result

class ValidationResults(val exampleValidationResults: Map<String, Result>, val hookValidationResult: Result) {
    val success: Boolean
        get() {
            if(exampleValidationResults.containsOnlyCompleteFailures())
                return false

            if(hookValidationResult is Result.Failure && !hookValidationResult.isPartialFailure())
                return false

            return true

        }

    val exitCode: Int
        get() {
            if(success) return SUCCESS_EXIT_CODE
            return FAILURE_EXIT_CODE
        }

    private fun Map<String, Result>.containsOnlyCompleteFailures(): Boolean {
        return this.any { it.value is Result.Failure && !it.value.isPartialFailure() }
    }

    companion object {
        fun forNoExamples(): ValidationResults {
            return ValidationResults(emptyMap(), Result.Success())
        }

        fun forOnlyExamples(exampleValidationResults: Map<String, Result>): ValidationResults {
            return ValidationResults(exampleValidationResults, Result.Success())
        }
    }
}

fun List<ValidationResults>.ofAllExamples() =
    flatMap { it.exampleValidationResults.entries }.associate { entry -> entry.toPair() }

fun List<ValidationResults>.mergedAsOne() =
    ValidationResults(ofAllExamples(), Result.fromResults(this.map { it.hookValidationResult }))

fun List<ValidationResults>.exitCode(): Int {
    return if (any { !it.success })
        FAILURE_EXIT_CODE
    else
        SUCCESS_EXIT_CODE
}
