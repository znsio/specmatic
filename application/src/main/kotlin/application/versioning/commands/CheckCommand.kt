package application.versioning.commands

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.*
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "check", mixinStandardHelpOptions = true, description = ["Check backward compatibility of all versions in a directory"])
class CheckCommand: Callable<Unit> {
    @Parameters(index = "0", description = ["Name of the directory"])
    var directory: String = ""

    @Parameters(index = "1", description = ["Version"])
    var version: Int = 0

    override fun call() {
        val result = testBackwardCompatibilityInDirectory(File(directory), version)
        val (exitValue, message) = when(result) {
            is JustOne -> Pair(0, "There was just one contract: ${result.file}")
            is ResultList -> {
                val failure = result.list.firstOrNull { !it.results.success() }
                when(failure) {
                    null -> Pair(0, "Contracts are all backward compatible.")
                    else -> Pair(1, """Backward compatibility breakdown detected.
${failure.older} => ${failure.newer}
${failure.results.report()}
                    """.trimIndent())
                }
            }
            Nothing -> Pair(0, "There were no files with this version number")
        }

        println(message)
        exitProcess(exitValue)
    }
}
