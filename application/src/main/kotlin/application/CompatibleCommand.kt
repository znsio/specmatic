package application

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.ContractBehaviour
import run.qontract.core.testBackwardCompatibility
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "compatible",
        mixinStandardHelpOptions = true,
        description = ["Test backward compatibility of a new contract"])
class CompatibleCommand : Callable<Void> {
    @Parameters(index = "0", description = ["Older contract file"], paramLabel = "<older contract path>")
    lateinit var olderFilePath: String

    @Parameters(index = "1", description = ["Newer contract file"], paramLabel = "<newer contract path>")
    lateinit var newerFilePath: String

    override fun call(): Void? {
        val older = ContractBehaviour(File(olderFilePath).readText())
        val newer = ContractBehaviour(File(newerFilePath).readText())
        val results = testBackwardCompatibility(older, newer)

        if(results.failureCount > 0) {
            println(results.report())
            exitProcess(1)
        } else {
            println("Older and newer contracts are compatible.")
        }

        return null
    }
}
