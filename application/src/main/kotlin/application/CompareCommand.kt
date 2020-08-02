package application

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.Feature
import run.qontract.core.pattern.ContractException
import run.qontract.core.testBackwardCompatibility2
import run.qontract.core.utilities.readFile
import java.util.concurrent.Callable

@Command(name = "compare",
        mixinStandardHelpOptions = true,
        description = ["Checks if two contracts are equivalent"])
class CompareCommand : Callable<Void> {
    @Parameters(index = "0", description = ["Contract path"])
    lateinit var path1: String

    @Parameters(index = "1", description = ["Contract path"])
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
    val behaviour1 = Feature(readFile(path1))
    val behaviour2 = Feature(readFile(path2))

    showPath(path1, path2)
    val successWith1To2 = backwardCompatible(behaviour1, behaviour2)
    if(successWith1To2) println("| All good.")

    println()
    showPath(path2, path1)
    val successWith2To1 = backwardCompatible(behaviour2, behaviour1)
    if(successWith2To1) println("| All good.")

    return Pair(successWith1To2, successWith2To1)
}

fun backwardCompatible(behaviour1: Feature, behaviour2: Feature): Boolean =
        try {
            testBackwardCompatibility2(behaviour1, behaviour2).let { results ->
                when {
                    results.failureCount > 0 -> {
                        println(results.report().prependIndent("| "))
                        false
                    }
                    else -> true
                }
            }
        } catch(e: ContractException) {
            println(e.report().prependIndent("| "))
            false
        } catch(e: Throwable) {
            println("Another error: " + e.localizedMessage)
            false
        }
