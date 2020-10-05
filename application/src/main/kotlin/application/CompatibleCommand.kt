package application

import org.springframework.beans.factory.annotation.Autowired
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.Feature
import run.qontract.core.Results
import run.qontract.core.git.GitCommand
import run.qontract.core.git.SystemGit
import run.qontract.core.testBackwardCompatibility
import run.qontract.core.utilities.exceptionCauseMessage
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "git",
        mixinStandardHelpOptions = true,
        description = ["Checks backward compatibility of a contract in a git repository"])
class GitCompatibleCommand : Callable<Unit> {
    @Autowired
    lateinit var fileOperations: FileOperations

    @Autowired
    lateinit var systemGit: SystemGit

    @Command(name = "file", description = ["Compare file in working tree against HEAD"])
    fun file(@Parameters(paramLabel = "contractPath") contractPath: String) {
        checkCompatibility {
            backwardCompatibleFile(contractPath, fileOperations, systemGit)
        }.execute()
    }

    @Command(name = "commits", description = ["Compare file in newer commit against older commit"])
    fun commits(@Parameters(paramLabel = "contractPath") path: String, @Parameters(paramLabel = "newerCommit") newerCommit: String, @Parameters(paramLabel = "olderCommit") olderCommit: String) {
        checkCompatibility {
            backwardCompatibleCommit(path, newerCommit, olderCommit, systemGit)
        }.execute()
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

internal fun compatibilityReport(results: Results, resultMessage: String): String {
    val countsMessage = "Tests run: ${results.successCount + results.failureCount}, Passed: ${results.successCount}, Failed: ${results.failureCount}\n\n"
    val resultReport = results.report().trim().let {
        when {
            it.isNotEmpty() -> "$it\n\n"
            else -> it
        }
    }

    return "$countsMessage$resultReport$resultMessage".trim()
}

internal fun backwardCompatibleFile(newerContractPath: String, fileOperations: FileOperations, git: GitCommand): OutCome<Results> {
    val newerFeature = Feature(fileOperations.read(newerContractPath))
    val result = getOlderFeature(newerContractPath, git)

    return result.onSuccess {
        OutCome(testBackwardCompatibility(it, newerFeature))
    }
}

internal fun backwardCompatibleCommit(contractPath: String, newerCommit: String, olderCommit: String, git: GitCommand): OutCome<Results> {
    val (gitRoot, relativeContractPath) = git.relativeGitPath(contractPath)

    val partial = PartialCommitFetch(gitRoot, relativeContractPath, contractPath)

    return partial.apply(newerCommit).onSuccess { newerGherkin ->
        partial.apply(olderCommit).onSuccess { olderGherkin ->
            OutCome(testBackwardCompatibility(Feature(olderGherkin), Feature(newerGherkin)))
        }
    }
}

internal fun getOlderFeature(newerContractPath: String, git: GitCommand): OutCome<Feature> {
    if(!git.fileIsInGitDir(newerContractPath))
        return OutCome(null, "Older contract file must be provided, or the file must be in a git directory")

    val(contractGit, relativeContractPath) = git.relativeGitPath(newerContractPath)
    return OutCome(Feature(contractGit.show("HEAD", relativeContractPath)))
}

internal data class CompatibilityOutput(val exitCode: Int, val message: String) {
    fun execute() {
        println(message)
        exitProcess(exitCode)
    }
}

internal fun compatibilityMessage(results: OutCome<Results>): CompatibilityOutput {
    return when {
        results.result == null -> CompatibilityOutput(0, results.errorMessage)
        results.result.success() -> CompatibilityOutput(0, "The newer contract is backward compatible")
        else -> CompatibilityOutput(1, compatibilityReport(results.result, "The newer contract is NOT backward compatible"))
    }
}

internal fun checkCompatibility(compatibilityCheck: () -> OutCome<Results>): CompatibilityOutput =
    try {
        val results = compatibilityCheck()
        compatibilityMessage(results)
    } catch(e: Throwable) {
        CompatibilityOutput(1, "Could not run backwad compatibility check, got exception\n${exceptionCauseMessage(e)}")
    }
