package application

import picocli.CommandLine
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.Configuration.Companion.configFileName
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.resultReport
import `in`.specmatic.core.utilities.*
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(name = "subscribe", description = ["Register for the project pipeline to be executed when a contract changes"], mixinStandardHelpOptions = true)
class SubscribeCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".$CONTRACT_EXTENSION/repos")
        val manifestFile = File(configFileName)
        val manifestData = try { loadConfigJSON(manifestFile) } catch(e: ContractException) { exitWithMessage(resultReport(e.failure())) }
        val sources = try { loadSources(manifestData) } catch(e: ContractException) { exitWithMessage(resultReport(e.failure())) }

        for(source in sources) {
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