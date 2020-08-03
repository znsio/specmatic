package application

import picocli.CommandLine
import picocli.CommandLine.*
import run.qontract.core.Feature
import run.qontract.core.git.GitCommand
import run.qontract.core.testBackwardCompatibility2
import run.qontract.core.utilities.exitWithMessage
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "compatible",
        mixinStandardHelpOptions = true,
        description = ["Checks if the newer contract is backward compatible with the older one"])
class BackwardCompatibleCommand : Callable<Unit> {
    @Parameters(description = ["Newer contract path"])
    lateinit var newerContractPath: String

    @Option(names = ["--older"], description = ["Older contract path"], required = false)
    var olderContractPath: String? = null

    override fun call() {
        val newerFeature = Feature(File(newerContractPath).readText())

        val olderFeature = when (olderContractPath) {
            null -> {
                if(!fileIsInGitDir(newerContractPath))
                    exitWithMessage("Older contract file must be provifed, or the file must be in a git directory")

                val gitRoot = File(GitCommand(File(newerContractPath).absoluteFile.parent).gitRoot())
                println("Git root: ${gitRoot.absolutePath}")
                val git = GitCommand(gitRoot.absolutePath)
                println("Contract path: ${File(newerContractPath).absolutePath}")
                val relativeContractPath = File(newerContractPath).absoluteFile.relativeTo(gitRoot.absoluteFile).path
                println("Relative to git root: $relativeContractPath")
                Feature(git.show("HEAD", relativeContractPath))
            }
            else -> Feature(File(newerContractPath).readText())
        }

        val results = testBackwardCompatibility2(olderFeature, newerFeature)

        val (resultExitCode, resultMessage) = when {
            results.success() -> Pair(0, "The newer is backward compatible with the older.")
            else -> Pair(1, "The newer is NOT backward compatible with the older.")
        }

        val countsMessage = "Tests run: ${results.successCount + results.failureCount}, Passed: ${results.successCount}, Failed: ${results.failureCount}\n\n"
        val resultReport = results.report().trim().let {
            when {
                it.isNotEmpty() -> "$it\n\n"
                else -> it
            }
        }

        println("$countsMessage$resultReport$resultMessage".trim())

        exitProcess(resultExitCode)
    }

    private fun fileIsInGitDir(newerContractPath: String): Boolean {
        val parentDir = File(newerContractPath).parentFile.absolutePath
        return GitCommand(workingDirectory = parentDir).workingDirectoryIsGitRepo()
    }
}
