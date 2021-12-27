package application.test

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import kotlin.system.exitProcess

class ContractExecutionListener : TestExecutionListener {
    private var success: Int = 0
    private var failure: Int = 0
    private var aborted: Int = 0

    private val failedLog: MutableList<String> = mutableListOf()

    private var couldNotStart = false;

    override fun executionSkipped(testIdentifier: TestIdentifier?, reason: String?) {
        super.executionSkipped(testIdentifier, reason)
    }

    override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
        if (listOf("SpecmaticJUnitSupport", "contractAsTest()", "JUnit Jupiter").any {
                    testIdentifier!!.displayName.contains(it)
                }) {
                    if(testExecutionResult?.status != TestExecutionResult.Status.SUCCESSFUL)
                        couldNotStart = true

                    return
        }

        val color: Ansi = when(testExecutionResult?.status) {
            TestExecutionResult.Status.SUCCESSFUL -> ansi().fgGreen()
            TestExecutionResult.Status.ABORTED -> ansi().fgYellow()
            TestExecutionResult.Status.FAILED -> ansi().fgBrightRed()
            else -> ansi()
        }

        println(color.a("${testIdentifier?.displayName} ${testExecutionResult?.status}").reset())

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

        val message = "Tests run: ${success + aborted + failure}, Failures: $failure, Aborted: $aborted"

        val color = when {
            failure > 0 -> ansi().bgBrightRed().fgBlack()
            aborted > 0 -> ansi().fgYellow()
            else -> ansi().fgGreen()
        }

        println(color.a(message).reset())

        if (failedLog.isNotEmpty()) {
            println()
            println(ansi().fgBrightRed().a("Unsuccessful scenarios:").reset())
            println(failedLog.joinToString(System.lineSeparator()) { it.prependIndent("  ") })
        }
    }

    fun exitProcess() {
        val exitStatus = when (failure != 0 || couldNotStart) {
            true -> 1
            false -> 0
        }

        exitProcess(exitStatus)
    }
}