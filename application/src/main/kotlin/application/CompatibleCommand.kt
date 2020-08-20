package application

import picocli.CommandLine
import picocli.CommandLine.*
import run.qontract.core.Feature
import run.qontract.core.Results
import run.qontract.core.git.GitCommand
import run.qontract.core.git.SystemGit
import run.qontract.core.testBackwardCompatibility
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.exitWithMessage
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "git",
        mixinStandardHelpOptions = true,
        description = ["Checks backward compatibility of a contract in a git repository"])
class GitCompatibleCommand : Callable<Unit> {
    @Command(name = "file", description = ["Compare file in working tree against HEAD"])
    fun file(@Parameters(paramLabel = "contractPath") contractPath: String) {
        val results = backwardCompatibleFile(contractPath, RealFileReader(), SystemGit())

        val (resultExitCode, resultMessage) = compatibilityMessage(results)

        printCompatibityReport(results, resultMessage)

        exitProcess(resultExitCode)
    }

    @Command(name = "commits", description = ["Compare file in newer commit against older commit"])
    fun commits(@Parameters(paramLabel = "contractPath") path: String, @Parameters(paramLabel = "newerCommit") newerCommit: String, @Parameters(paramLabel = "olderCommit") olderCommit: String) {
        try {
            val results = backwardCompatibleCommit(path, newerCommit, olderCommit, SystemGit())

            if (results != null) {
                val (resultExitCode, resultMessage) = compatibilityMessage(results)
                printCompatibityReport(results, resultMessage)
                exitProcess(resultExitCode)
            } else {
                print("One of the commits was not found")
            }
        } catch(e: Throwable) {
            println("Could not run backwad compatibility check, got exception\n${exceptionCauseMessage(e)}")
        }
    }

    override fun call() {
        CommandLine(GitCompatibleCommand()).usage(System.out)
    }
}

@Command(name = "compatible",
        mixinStandardHelpOptions = true,
        description = ["Checks if the newer contract is backward compatible with the older one"],
        subcommands = [ GitCompatibleCommand::class ])
internal class CompatibleCommand : Callable<Unit> {
    override fun call() {
        CommandLine(CompatibleCommand()).usage(System.out)
    }
}

internal fun printCompatibityReport(results: Results, resultMessage: String) {
    val countsMessage = "Tests run: ${results.successCount + results.failureCount}, Passed: ${results.successCount}, Failed: ${results.failureCount}\n\n"
    val resultReport = results.report().trim().let {
        when {
            it.isNotEmpty() -> "$it\n\n"
            else -> it
        }
    }

    println("$countsMessage$resultReport$resultMessage".trim())
}

internal fun backwardCompatibleFile(newerContractPath: String, reader: FileReader, git: GitCommand): Results {
    val newerFeature = Feature(reader.read(newerContractPath))
    val olderFeature = getOlderFeature(newerContractPath, git)

    return testBackwardCompatibility(olderFeature, newerFeature)
}

internal fun backwardCompatibleCommit(contractPath: String, newerCommit: String, olderCommit: String, git: GitCommand): Results? {
    val (gitRoot, relativeContractPath) = git.relativeGitPath(contractPath)
    val newerGherkin = try {
        gitRoot.show(newerCommit, relativeContractPath).trim()
    } catch (e: Throwable) {
        println("Could not load ${newerCommit}:${contractPath}")
        ""
    }

    val olderGherkin = try {
        gitRoot.show(olderCommit, relativeContractPath).trim()
    } catch (e: Throwable) {
        println("Could not load ${olderCommit}:${contractPath}")
        ""
    }

    return if (listOf(newerGherkin, olderGherkin).any { it.isEmpty() }) {
        null
    }
    else {
        val olderFeature = Feature(olderGherkin)
        val newerFeature = Feature(newerGherkin)

        testBackwardCompatibility(olderFeature, newerFeature)
    }
}

internal fun getOlderFeature(newerContractPath: String, git: GitCommand): Feature {
    if(!git.fileIsInGitDir(newerContractPath))
        exitWithMessage("Older contract file must be provided, or the file must be in a git directory")

    val (contractGit, relativeContractPath) = git.relativeGitPath(newerContractPath)
    return Feature(contractGit.show("HEAD", relativeContractPath))
}

internal data class CompatibilityOutput(val exitCode: Int, val message: String)

internal fun compatibilityMessage(results: Results): CompatibilityOutput {
    return when {
        results.success() -> CompatibilityOutput(0, "The newer contract is backward compatible")
        else -> CompatibilityOutput(1, "The newer contract is NOT backward compatible")
    }
}
