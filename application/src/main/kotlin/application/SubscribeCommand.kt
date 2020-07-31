package application

import picocli.CommandLine
import run.qontract.core.git.GitCommand
import run.qontract.core.git.NonZeroExitError
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.core.utilities.loadJSONFromManifest
import run.qontract.core.utilities.loadSourceDataFromManifest
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(name = "subscribe", description = ["Register for the project pipeline to be executed when a contract changes"], mixinStandardHelpOptions = true)
class SubscribeCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".qontract/repos")
        val manifestFile = File("./qontract.json")
        val manifestData = loadJSONFromManifest(manifestFile)
        val sources = loadSourceDataFromManifest(manifestData)

        for(source in sources) {
            val sourceDir = source.directoryRelativeTo(workingDirectory)
            val sourceGit = GitCommand(sourceDir.path)

            try {
                if (sourceGit.workingDirectoryIsGitRepo()) {
                    sourceGit.pull()

                    for(contract in source.contracts) {
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