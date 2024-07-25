package io.specmatic.test.listeners

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestIdentifier

class MonochromePrinter: ContractExecutionPrinter {
    override fun printFinalSummary(testSummary: TestSummary) {
        println(testSummary.message)
        println()
        println("Executed at ${currentDateAndTime()}")
    }

    override fun printTestSummary(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
        println(testStatusMessage(testIdentifier, testExecutionResult))
    }

    override fun printFailureTitle(failures: String) {
        println(failures)
    }
}

fun currentDateAndTime(): String {
    return java.time.LocalDateTime.now().toString()
}
