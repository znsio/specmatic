package application.test

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

class ContractExecutionListener : TestExecutionListener {
    private var success: Int = 0
    private var failure: Int = 0

    override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {

        if (listOf("QontractJUnitSupport", "contractAsTest()", "JUnit Jupiter").any {
                    testIdentifier!!.displayName.contains(it)
                }) return

        println("${testIdentifier?.displayName} ${testExecutionResult?.status}")
        testExecutionResult?.status?.name?.equals("SUCCESSFUL").let {
            when (it) {
                false -> {
                    failure++
                    val message = testExecutionResult?.throwable?.get()?.message?.replace("\n", "\n\t")
                    println("Reason: $message")
                }
                else -> {
                    success++
                    println()
                }
            }
        }
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan?) {
        println("Tests run: ${success + failure}, Failures: ${failure}")
    }

    fun exitProcess() {
        when (failure != 0) {
            true -> kotlin.system.exitProcess(1)
            false -> kotlin.system.exitProcess(0)
        }
    }
}