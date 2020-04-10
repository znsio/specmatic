package application

import run.qontract.core.utilities.readFile
import picocli.CommandLine.*
import run.qontract.core.Contract
import run.qontract.core.ContractBehaviour
import run.qontract.core.pattern.ContractException
import run.qontract.core.resultReport
import run.qontract.core.testBackwardCompatibility
import run.qontract.fake.ContractFake
import java.util.concurrent.Callable

@Command(name = "compare",
        mixinStandardHelpOptions = true,
        description = ["Checks if two contracts are equivalent"])
class CompareCommand : Callable<Void> {
    @Parameters(index = "0", description = ["Contract path"], paramLabel = "<contract path>")
    lateinit var path1: String

    @Parameters(index = "1", description = ["Contract path"], paramLabel = "<contract path>")
    lateinit var path2: String



    override fun call(): Void? {
        val (successWith1To2, successWith2To1) = mutualCompatibility(path1, path2)
        val both = successWith1To2 && successWith2To1

        println()

        println(when {
            both -> "The contracts are mutually compatible."
            successWith1To2 -> "$path2 is backward compatible with $path1."
            successWith2To1 -> "$path1 is backward compatible with $path2."
            else -> "The contracts are mutually incompatible."
        })

        return null
    }
}

private fun showPath(path1: String, path2: String) {
    println("| $path1 => $path2")
}

private fun mutualCompatibility(path1: String, path2: String): Pair<Boolean, Boolean> {
    val behaviour1 = ContractBehaviour(readFile(path1))
    val behaviour2 = ContractBehaviour(readFile(path2))

    showPath(path1, path2)
    val successWith1To2 = backwardCompatible(behaviour1, behaviour2)

    println()
    showPath(path2, path1)
    val successWith2To1 = backwardCompatible(behaviour2, behaviour1)

    return Pair(successWith1To2, successWith2To1)
}

fun backwardCompatible(behaviour1: ContractBehaviour, behaviour2: ContractBehaviour): Boolean =
        testBackwardCompatibility(behaviour1, behaviour2).let { results ->
            when {
                results.failureCount > 0 -> {
                    println(results.report().prependIndent("| "))
                    false
                }
                else -> true
            }
        }
