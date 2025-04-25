package io.specmatic.core.examples.module

import io.specmatic.core.Result

private const val SUCCESS_EXIT_CODE = 0
private const val FAILURE_EXIT_CODE = 1

class ValidationResult(private val exampleValidationResult: Result, private val hookValidationResult: Result) {
    private val success: Boolean
        get() {
            if(exampleValidationResult is Result.Failure && !exampleValidationResult.isPartialFailure()) {
                return false
            }

            if(hookValidationResult is Result.Failure && !hookValidationResult.isPartialFailure()) {
                return false
            }

            return true
        }

    val exitCode: Int
        get() {
            if(success)
                return SUCCESS_EXIT_CODE

            return FAILURE_EXIT_CODE
        }

    val errorMessage: String?
        get() {
            val combinedResult = Result.Failure.fromFailures(listOf(exampleValidationResult, hookValidationResult).filterIsInstance<Result.Failure>())
            return combinedResult.reportString().takeIf { it.isNotBlank() }
        }
}

class ValidationResults(val ofExamples: Map<String, Result>, val ofHook: Result) {

    fun exitCode(): Int = when (ofExamples.containsOnlyCompleteFailures()) {
        true -> FAILURE_EXIT_CODE
        false -> exitCodeBasedOnHookResult()
    }

    fun exitCodeBasedOnHookResult() = when (ofHook.isSuccess()) {
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
    flatMap { it.ofExamples.entries }.associate { entry -> entry.toPair() }

fun List<ValidationResults>.exitCode() = when(any { it.exitCode() == FAILURE_EXIT_CODE }) {
        true -> FAILURE_EXIT_CODE
        else -> SUCCESS_EXIT_CODE
    }
