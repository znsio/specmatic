package application

import run.qontract.core.utilities.readFile
import picocli.CommandLine.*
import run.qontract.core.ContractBehaviour
import run.qontract.core.testBackwardCompatibility
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "equal",
        mixinStandardHelpOptions = true,
        description = ["Test if two contract are equivalent"])
class EqualCommand : Callable<Void> {
    @Parameters(index = "0", description = ["Older contract path"], paramLabel = "<older contract path>")
    lateinit var path1: String

    @Parameters(index = "1", description = ["Newer contract path"], paramLabel = "<newer contract path>")
    lateinit var path2: String

    override fun call(): Void? {
        val behaviour1 = ContractBehaviour(readFile(path1))
        val behaviour2 = ContractBehaviour(readFile(path2))

        if (!backwardCompatible(behaviour1, behaviour2)) {
            exitProcess(1)
        } else if (!backwardCompatible(behaviour2, behaviour1)) {
            exitProcess(1)
        }

        println("The contracts at $path1 and $path2 are equivalent.")
        return null
    }
}

fun backwardCompatible(behaviour1: ContractBehaviour, behaviour2: ContractBehaviour): Boolean =
        testBackwardCompatibility(behaviour1, behaviour2).let { results ->
            when {
                results.failureCount > 0 -> {
                    println(results.report())
                    false
                }
                else -> true
            }
        }
