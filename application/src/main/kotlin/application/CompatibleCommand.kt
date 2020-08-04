package application

import picocli.CommandLine.*
import run.qontract.core.Feature
import run.qontract.core.Results
import run.qontract.core.git.GitCommand
import run.qontract.core.git.SystemGit
import run.qontract.core.testBackwardCompatibility2
import run.qontract.core.utilities.exitWithMessage
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "compatible",
        mixinStandardHelpOptions = true,
        description = ["Checks if the newer contract is backward compatible with the older one"])
class CompatibleCommand : Callable<Unit> {
    @Parameters(description = ["Newer contract path"])
    lateinit var newerContractPath: String

    @Option(names = ["--older"], description = ["Older contract path"], required = false)
    var olderContractPath: String? = null

    override fun call() {
        val results = backwardCompatibilityCommand(newerContractPath, olderContractPath, RealFileReader(), SystemGit())

        val (resultExitCode, resultMessage) = compatibilityMessage(results)

        printCompatibityReport(results, resultMessage)

        exitProcess(resultExitCode)
    }

    private fun printCompatibityReport(results: Results, resultMessage: String) {
        val countsMessage = "Tests run: ${results.successCount + results.failureCount}, Passed: ${results.successCount}, Failed: ${results.failureCount}\n\n"
        val resultReport = results.report().trim().let {
            when {
                it.isNotEmpty() -> "$it\n\n"
                else -> it
            }
        }

        println("$countsMessage$resultReport$resultMessage".trim())
    }
}

internal fun backwardCompatibilityCommand(newerContractPath: String, olderContractPath: String?, reader: FileReader, git: GitCommand): Results {
    val newerFeature = Feature(reader.read(newerContractPath))
    val olderFeature = getOlderFeature(newerContractPath, olderContractPath, reader, git)

    return testBackwardCompatibility2(olderFeature, newerFeature)
}

internal fun getOlderFeature(newerContractPath: String, olderContractPath: String?, reader: FileReader, git: GitCommand): Feature {
    return when(olderContractPath) {
        null -> {
            if(!git.fileIsInGitDir(newerContractPath))
                exitWithMessage("Older contract file must be provided, or the file must be in a git directory")

            val (contractGit, relativeContractPath) = git.relativeGitPath(newerContractPath)
            Feature(contractGit.show("HEAD", relativeContractPath))
        }
        else -> Feature(reader.read(newerContractPath))
    }
}

data class CompatibilityOutput(val exitCode: Int, val message: String)

internal fun compatibilityMessage(results: Results): CompatibilityOutput {
    return when {
        results.success() -> CompatibilityOutput(0, "The newer contract is backward compatible")
        else -> CompatibilityOutput(1, "The newer contract is NOT backward compatible")
    }
}
