package io.specmatic.test.listeners

import io.specmatic.core.log.logger
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestIdentifier

class MonochromePrinter: ContractExecutionPrinter {
    override fun printFinalSummary(testSummary: TestSummary) {
        println(testSummary.message)
        println()
        println("Executed at ${currentDateAndTime()}")
    }

    override fun printTestSummary(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
        logger.debug(testStatusMessage(testIdentifier, testExecutionResult) + System.lineSeparator())
    }

    override fun printFailureTitle(failures: String) {
        println(failures)
    }
}

fun currentDateAndTime(): String {
    return java.time.LocalDateTime.now().toString()
}
