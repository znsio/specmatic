package application.test

import `in`.specmatic.core.log.logger
import `in`.specmatic.test.SpecmaticJUnitSupport
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

data class Test(val name: String, var status: String = "DID NOT RUN") {
    constructor(identifier: TestIdentifier): this(identifier.displayName)
}

data class TestId(private val id: String) {
    constructor(identifier: TestIdentifier): this(identifier.uniqueId)
}

data class TestSuite(val name: String, private val identifier: String, val tests: MutableMap<TestId, Test> = linkedMapOf()) {
    constructor(identifier: TestIdentifier): this(identifier.displayName, identifier.uniqueId)
}

class ContractExecutionListener : TestExecutionListener {
    private var success: Int = 0
    private var failure: Int = 0
    private var aborted: Int = 0

    private val failedLog: MutableList<String> = mutableListOf()

    private var couldNotStart = false;

    private val colorPrinter: ContractExecutionPrinter = getContractExecutionPrinter()

    private val testSuites = linkedMapOf<TestId, TestSuite>()
    private var currentTestSuite: TestSuite? = null

    override fun dynamicTestRegistered(testIdentifier: TestIdentifier?) {
        super.dynamicTestRegistered(testIdentifier)

        if(testIdentifier == null)
            return

        if(testIdentifier.isContainer) {
            val testSuite = TestSuite(testIdentifier)

            testSuites[TestId(testIdentifier)] = testSuite
            currentTestSuite = testSuite
        }

        if(testIdentifier.isTest)
            currentTestSuite?.let {
                it.tests[TestId(testIdentifier)] = Test(testIdentifier)
            }
    }

    override fun executionStarted(testIdentifier: TestIdentifier?) {
        super.executionStarted(testIdentifier)

        if(testIdentifier == null)
            return

        if(testIdentifier.isContainer)
            currentTestSuite = testSuites[TestId(testIdentifier)]
    }

    override fun executionSkipped(testIdentifier: TestIdentifier?, reason: String?) {
        super.executionSkipped(testIdentifier, reason)

        if(testIdentifier == null)
            return

        if(testIdentifier.isTest) {
            currentTestSuite?.let {
                val test = it.tests[TestId(testIdentifier)]

                if(test != null)
                    test.status = "SKIPPED"
            }
        }
    }

    override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
        if (listOf("SpecmaticJUnitSupport", "backwardCompatibilityTest()", "contractTest()", "JUnit Jupiter", "JUnitBackwardCompatibilityTestRunner").any {
                    testIdentifier!!.displayName.contains(it)
                }) {
                    if(testExecutionResult?.status != TestExecutionResult.Status.SUCCESSFUL)
                        couldNotStart = true

                    return
        }

        if(testIdentifier == null)
            return

        if(testIdentifier.isContainer)
            return

        colorPrinter.printTestSummary(testIdentifier, testExecutionResult)

        updateTestStatus(testIdentifier, testExecutionResult)

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
                logger.debug("A test called \"${testIdentifier.displayName}\" ran but the test execution result was null. Please inform the Specmatic developer.")
            }
        }
    }

    private fun updateTestStatus(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult?) {
        if(testExecutionResult == null)
            return

        currentTestSuite?.let {
            val test = it.tests[TestId(testIdentifier)] ?: return
            test.status = testExecutionResult.status.name
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

        if(SpecmaticJUnitSupport.partialSuccesses.isNotEmpty()) {
            println()
            colorPrinter.printFailureTitle("Partial Successes:")
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
            colorPrinter.printFailureTitle("Unsuccessful Scenarios:")
            println(failedLog.joinToString(System.lineSeparator()) { it.prependIndent("  ") })
            println()
        }

        colorPrinter.printFinalSummary(TestSummary(success, SpecmaticJUnitSupport.partialSuccesses.size, aborted, failure))
    }

    fun exitProcess() {
        val exitStatus = when (failure != 0 || couldNotStart) {
            true -> 1
            false -> 0
        }

        exitProcess(exitStatus)
    }
}