package io.specmatic.test.listeners

import io.specmatic.core.log.logger
import io.specmatic.test.SpecmaticJUnitSupport
import io.specmatic.test.status.TestExecutionStatus
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import kotlin.system.exitProcess

fun getContractExecutionPrinter(): ContractExecutionPrinter {
    return if(stdOutIsRedirected())
        MonochromePrinter()
    else if(colorIsRequested())
        ColorPrinter()
    else MonochromePrinter()
}

private fun colorIsRequested() = System.getenv("SPECMATIC_COLOR") == "1"

private fun stdOutIsRedirected() = System.console() == null

class ContractExecutionListener : TestExecutionListener {

    companion object {
        private var success: Int = 0
        private var failure: Int = 0
        private var aborted: Int = 0

        private val failedLog: MutableList<String> = mutableListOf()
        private var couldNotStart = false
        private val exceptionsThrown = mutableListOf<Throwable>()
        private val printer: ContractExecutionPrinter = getContractExecutionPrinter()

        /**
         * Check if any tests ran during execution
         * @return true if at least one test ran, false otherwise
         */
        fun testsRan(): Boolean {
            return success + failure + aborted > 0
        }

        /**
         * Get the appropriate exit code based on test execution results
         * @param exitWithErrorOnNoTests Whether to return an error code when no tests run
         * @return 0 for success, non-zero for failure
         */
        fun getExitCode(exitWithErrorOnNoTests: Boolean): Int {
            return when {
                failure > 0 || couldNotStart -> 1
                !testsRan() && exitWithErrorOnNoTests -> 1
                else -> 0
            }
        }

        fun exitProcess() {
            // If there were test failures or we couldn't start, mark it in TestExecutionStatus
            if (failure != 0 || couldNotStart) {
                TestExecutionStatus.markTestFailure()
            }
            // Use our internal getExitCode method with default value of true for exitWithErrorOnNoTests
            exitProcess(getExitCode(true))
        }
    }

    override fun executionSkipped(testIdentifier: TestIdentifier?, reason: String?) {
        super.executionSkipped(testIdentifier, reason)
    }

    override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
        if (testIdentifier != null &&
            testIdentifier.type == TestDescriptor.Type.CONTAINER
            ) {

            testExecutionResult?.let {
                it.throwable?.ifPresent { throwable -> exceptionsThrown.add(throwable) }
                couldNotStart = it.status != TestExecutionResult.Status.SUCCESSFUL
            }

            return
        }

        printer.printTestSummary(testIdentifier, testExecutionResult)

        when(testExecutionResult?.status) {
            TestExecutionResult.Status.SUCCESSFUL ->  {
                success++
                println()
            }
            TestExecutionResult.Status.ABORTED -> {
                aborted++
                printAndLogFailure(testExecutionResult, testIdentifier)
            }
            TestExecutionResult.Status.FAILED -> {
                failure++
                printAndLogFailure(testExecutionResult, testIdentifier)
            }
            else -> {
                logger.debug("A test called \"${testIdentifier?.displayName}\" ran but the test execution result was null. Please inform the Specmatic developer.")
            }
        }
    }

    private fun printAndLogFailure(
        testExecutionResult: TestExecutionResult,
        testIdentifier: TestIdentifier?
    ) {
        val message = testExecutionResult.throwable?.get()?.message?.replace("\n", "\n\t")?.trimIndent()
            ?: ""

        val reason = "Reason: $message"
        println("$reason\n\n")

        val log = """"${testIdentifier?.displayName} ${testExecutionResult.status}"
    ${reason.prependIndent("  ")}"""

        failedLog.add(log)
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan?) {
        org.fusesource.jansi.AnsiConsole.systemInstall()

        println()

        exceptionsThrown.map { exceptionThrown ->
            logger.log(exceptionThrown)
        }

        println()

        if(SpecmaticJUnitSupport.partialSuccesses.isNotEmpty()) {
            println()
            printer.printFailureTitle("Partial Successes:")
            println()

            SpecmaticJUnitSupport.partialSuccesses.filter { it.partialSuccessMessage != null} .forEach { result ->
                println("  " + (result.scenario?.testDescription() ?: "Unknown Scenario"))
                println("    " + result.partialSuccessMessage!!)
                println()
            }

            println()
        }

        if (failedLog.isNotEmpty()) {
            println()
            printer.printFailureTitle("Unsuccessful Scenarios:")
            println(failedLog.joinToString(System.lineSeparator() + System.lineSeparator()) { it.prependIndent("  ") })
            println()
        }

        printer.printFinalSummary(TestSummary(success, SpecmaticJUnitSupport.partialSuccesses.size, aborted, failure))
    }
}
