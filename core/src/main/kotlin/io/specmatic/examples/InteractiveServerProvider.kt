package io.specmatic.examples

import io.ktor.server.application.*
import io.specmatic.core.TestResult
import java.io.File

interface InteractiveServerProvider {
    val serverHost: String
    val serverPort: Int
    val sutBaseUrl: String?

    val contractFile: File?
    val exampleTableColumns: List<ExampleTableColumn>

    suspend fun generateExample(call: ApplicationCall, contractFile: File): ExampleGenerationResult

    fun validateExample(contractFile: File, exampleFile: File): ExampleValidationResult

    fun testExample(contractFile: File, exampleFile: File, sutBaseUrl: String): ExampleTestResult

    fun getTableRows(contractFile: File): List<ExampleTableRow>

    fun ensureValidContractFile(contractFile: File):  Pair<File?, String?>

    fun ensureValidExampleFile(exampleFile: File): Pair<File?, String?>
}

data class ExamplePageRequest (
    val contractFile: File,
    val hostPort: String?
)

data class ExampleGenerationResponse (
    val exampleFilePath: String,
    val status: String
) {
    constructor(result: ExampleGenerationResult): this(
        exampleFilePath = result.exampleFile?.absolutePath ?: throw Exception("Failed to generate example file"),
        status = result.status.toString()
    )
}

data class ExampleValidationRequest (
    val exampleFile: File
)

data class ExampleValidationResponse (
    val exampleFilePath: String,
    val error: String? = null
) {
    constructor(result: ExampleValidationResult): this(
        exampleFilePath = result.exampleName, error = result.result.reportString().takeIf { it.isNotBlank() }
    )
}

data class ExampleContentRequest (
    val exampleFile: File
)

data class ExampleTestRequest (
    val exampleFile: File
)

data class ExampleTestResponse (
    val result: TestResult,
    val details: String,
    val testLog: String
) {
    constructor(result: ExampleTestResult): this (
        result = result.result,
        details = resultToDetails(result.result, result.exampleFile),
        testLog = result.testLog.trim('-', ' ', '\n', '\r')
    )

    companion object {
        fun resultToDetails(result: TestResult, exampleFile: File): String {
            val postFix = when(result) {
                TestResult.Success -> "has SUCCEEDED"
                TestResult.Error -> "has ERROR"
                else -> "has FAILED"
            }

            return "Example test for ${exampleFile.nameWithoutExtension} $postFix"
        }
    }
}

data class ExampleTableColumn (
    val name: String,
    val colSpan: Int
)

data class ExampleRowGroup (
    val columnName: String,
    val value: String,
    val rowSpan: Int = 1,
    val showRow: Boolean = true,
    val rawValue: String = value,
    val extraInfo: String? = null
)

data class ExampleTableRow (
    val columns: List<ExampleRowGroup>,
    val exampleFilePath: String? = null,
    val exampleFileName: String? = null,
    val exampleMismatchReason: String? = null
)