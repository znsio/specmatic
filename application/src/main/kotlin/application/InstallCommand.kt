package application

import picocli.CommandLine
import `in`.specmatic.core.APPLICATION_NAME
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.resultReport
import `in`.specmatic.core.utilities.exitWithMessage
import `in`.specmatic.core.utilities.loadConfigJSON
import `in`.specmatic.core.utilities.loadSources
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
