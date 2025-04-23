package io.specmatic.core.examples.module

import io.specmatic.core.Result

private const val SUCCESS_EXIT_CODE = 0
private const val FAILURE_EXIT_CODE = 1

class ValidationResult(val ofExample: Result, val ofHook: Result) {

    fun exitCodeBasedOnHookResult() = when (ofHook.isSuccess()) {
        true -> SUCCESS_EXIT_CODE
        false -> FAILURE_EXIT_CODE
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
