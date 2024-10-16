package io.specmatic.examples

import io.specmatic.core.Result
import io.specmatic.core.TestResult
import java.io.File

data class ExampleTestResult(val result: TestResult, val testLog: String, val exampleFile: File) {
    constructor(result: Result, testLog: String, exampleFile: File): this(
        result.testResult(),
        result.takeIf { !it.isSuccess() }?.let {
            "${it.reportString()}\n\n$testLog"
        } ?: testLog,
        exampleFile
    )
}
