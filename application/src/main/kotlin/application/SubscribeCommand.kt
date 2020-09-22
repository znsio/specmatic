package application

import picocli.CommandLine
import run.qontract.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import run.qontract.core.git.SystemGit
import run.qontract.core.git.NonZeroExitError
import run.qontract.core.utilities.commitAndPush
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.loadConfigJSON
import run.qontract.core.utilities.loadSources
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(name = "subscribe", description = ["Register for the project pipeline to be executed when a contract changes"], mixinStandardHelpOptions = true)
class SubscribeCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".qontract/repos")
        val manifestFile = File(DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY)
        val manifestData = loadConfigJSON(manifestFile)
        val sources = loadSources(manifestData)

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