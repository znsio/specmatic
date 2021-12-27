package application.test

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestIdentifier

interface ContractExecutionPrinter {
    fun printFinalSummary(testSummary: TestSummary)
    fun printTestSummary(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?)
    fun printFailureTitle(failures: String)
}

fun testStatusMessage(
    testIdentifier: TestIdentifier?,
    testExecutionResult: TestExecutionResult?
) = "${testIdentifier?.displayName} ${testExecutionResult?.status}"
