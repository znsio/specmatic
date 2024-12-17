package io.specmatic.core.examples.server

import io.specmatic.core.Result
import io.specmatic.core.TestResult
import java.io.File

data class ExampleTestResponse(
    val result: TestResult,
    val details: String,
    val testLog: String
) {
    constructor(result: Result, testLog: String, exampleFile: File): this (
        result = result.testResult(),
        details = resultToDetails(result, exampleFile),
        testLog = when(result.isSuccess()) {
            true -> testLog
            false -> "${result.reportString()}\n\n$testLog"
        }
    )

    companion object {
        fun resultToDetails(result: Result, exampleFile: File): String {
            val postFix = when(result.testResult()) {
                TestResult.Success -> "has SUCCEEDED"
                TestResult.Error -> "has ERROR"
                else -> "has FAILED"
            }

            return "Example test for ${exampleFile.nameWithoutExtension} $postFix"
        }
    }
}
