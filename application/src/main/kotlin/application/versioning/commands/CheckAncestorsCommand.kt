package application.versioning.commands

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.*
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "checkAncestors", mixinStandardHelpOptions = true, description = ["Check backward compatibility of all versions in a directory"])
class CheckAncestorsCommand: Callable<Unit> {
    @Parameters(index = "0", description = ["Name of the directory"])
    var directory: String = ""

    @Parameters(index = "1", description = ["Version"])
    var version: String = ""

    override fun call() {
        val versionTokens = version.split(".").map { it.toInt() }
        if(versionTokens.isEmpty() || versionTokens.size > 2) {
            println("Versions must be of the format <majorVersion>.<minorVersion>")
            exitProcess(1)
        }

        val majorVersion = versionTokens[0]
        val minorVersion = versionTokens.getOrNull(1)

        val result = testBackwardCompatibilityInDirectory(File(directory), majorVersion, minorVersion)
        val (exitValue, message) = when(result) {
            is JustOne -> Pair(0, "There was just one contract: ${result.filePath}")
            is TestResults -> {
                when(val failure = result.list.firstOrNull { !it.results.success() }) {
                    null -> Pair(0, "Contracts are all backward compatible.")
                    else -> Pair(1, """${failure.older} => ${failure.newer}
${failure.results.report()}

Backward compatibility breakdown detected between ${failure.older} and ${failure.newer}
                    """.trimIndent())
                }
            }
            NoContractsFound -> Pair(0, "There were no files with this version number")
        }

        println(message)
        exitProcess(exitValue)
    }
}
