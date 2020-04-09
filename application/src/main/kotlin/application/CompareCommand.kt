package application

import run.qontract.core.utilities.readFile
import picocli.CommandLine.*
import run.qontract.core.ContractBehaviour
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
        val behaviour1 = ContractBehaviour(readFile(path1))
        val behaviour2 = ContractBehaviour(readFile(path2))

        val successWith1To2 = backwardCompatible(behaviour1, behaviour2)
        val successWith2To1 = backwardCompatible(behaviour2, behaviour1)
        val both = successWith1To2 && successWith2To1

        println()
        println(when {
            both -> "The contracts are mutually backward compatible."
            successWith1To2 -> "$path2 is backward compatible with $path1."
            successWith2To1 -> "$path1 is backward compatible with $path2."
            else -> "The contracts are mutually incompatible."
        })

        return null
    }
}
