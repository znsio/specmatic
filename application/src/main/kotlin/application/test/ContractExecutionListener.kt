package application.test

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

class ContractExecutionListener : TestExecutionListener {
    private var success: Int = 0
    private var failure: Int = 0

    private val failedScenarios: MutableList<String> = mutableListOf()

    override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
        if (listOf("QontractJUnitSupport", "contractAsTest()", "JUnit Jupiter").any {
                    testIdentifier!!.displayName.contains(it)
                }) return

        println("${testIdentifier?.displayName} ${testExecutionResult?.status}")
        testExecutionResult?.status?.name?.equals("SUCCESSFUL").let {
            when (it) {
                false -> {
                    if(testIdentifier?.displayName != null)
                        failedScenarios.add(testIdentifier.displayName)

                    failure++
                    val message = testExecutionResult?.throwable?.get()?.message?.replace("\n", "\n\t")?.trimIndent() ?: ""
                    println("Reason: $message\n\n")
                }
                else -> {
                    success++
                    println()
                }
            }
        }
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan?) {
        println("Tests run: ${success + failure}, Failures: $failure")

        if(failedScenarios.isNotEmpty()) {
            println()
            println("Failed scenarios:")
            println(failedScenarios.distinct().joinToString(System.lineSeparator()) { it.prependIndent("  ")})
        }
    }

    fun exitProcess() {
        when (failure != 0) {
            true -> kotlin.system.exitProcess(1)
            false -> kotlin.system.exitProcess(0)
        }
    }
}