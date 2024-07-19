package application

import io.specmatic.core.APPLICATION_NAME_LOWER_CASE
import io.specmatic.core.Configuration.Companion.globalConfigFileName
import io.specmatic.core.git.NonZeroExitError
import io.specmatic.core.git.SystemGit
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.*
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(name = "subscribe", description = ["Register for the project pipeline to be executed when a contract changes"], mixinStandardHelpOptions = true)
class SubscribeCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".$APPLICATION_NAME_LOWER_CASE/repos")
        val manifestFile = File(globalConfigFileName)
        val manifestData = try { loadConfigJSON(manifestFile) } catch(e: ContractException) { exitWithMessage(e.failure().toReport().toText()) }
        val sources = try { loadSources(manifestData) } catch(e: ContractException) { exitWithMessage(e.failure().toReport().toText()) }

        val unsupportedSources = sources.filter { it !is GitSource }.mapNotNull { it.type }.distinct()

        if(unsupportedSources.isNotEmpty()) {
            println("The following types of sources are not supported: ${unsupportedSources.joinToString(", ")}")
        }

        val supportedSources = sources.filter { it is GitSource }

        for(source in supportedSources) {
            val sourceDir = source.directoryRelativeTo(workingDirectory)
            val sourceGit = SystemGit(sourceDir.path)

            try {
                if (sourceGit.workingDirectoryIsGitRepo()) {
                    sourceGit.pull()

                    for(contract in source.testContracts + source.stubContracts) {
                        val contractPath = sourceDir.resolve(File(contract))
                        subscribeToContract(manifestData, sourceDir.resolve(contractPath).path, sourceGit)
                    }

                    commitAndPush(sourceGit)
                }
            } catch (e: NonZeroExitError) {
                println("Couldn't push the latest. Got error: ${exceptionCauseMessage(e)}")
                exitProcess(1)
            }
        }
    }
}