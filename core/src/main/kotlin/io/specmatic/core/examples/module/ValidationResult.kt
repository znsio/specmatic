package io.specmatic.core.examples.module

import io.specmatic.core.Result

const val SUCCESS_EXIT_CODE = 0
const val FAILURE_EXIT_CODE = 1

class ValidationResult(val exampleValidationResult: Result, private val hookValidationResult: Result) {
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
