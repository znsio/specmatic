package application

import picocli.CommandLine
import run.qontract.core.APPLICATION_NAME
import run.qontract.core.CONTRACT_EXTENSION
import run.qontract.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import run.qontract.core.pattern.ContractException
import run.qontract.core.resultReport
import run.qontract.core.utilities.exitWithMessage
import run.qontract.core.utilities.loadConfigJSON
import run.qontract.core.utilities.loadSources
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "install", description = ["Clone the git repositories declared in the manifest"], mixinStandardHelpOptions = true)
class InstallCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".$CONTRACT_EXTENSION/repos")

        val sources = try { loadSources(DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY) } catch(e: ContractException) { exitWithMessage(resultReport(e.failure())) }

        for(source in sources) {
            println("Installing $source")
            source.install(workingDirectory)
        }
    }
}
