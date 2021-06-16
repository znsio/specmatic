package application

import picocli.CommandLine
import `in`.specmatic.core.APPLICATION_NAME_LOWER_CASE
import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.resultReport
import `in`.specmatic.core.utilities.exitWithMessage
import `in`.specmatic.core.utilities.loadSources
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "install", description = ["Clone the git repositories declared in the manifest"], mixinStandardHelpOptions = true)
class InstallCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".$APPLICATION_NAME_LOWER_CASE/repos")

        val sources = try { loadSources(globalConfigFileName) } catch(e: ContractException) { exitWithMessage(resultReport(e.failure())) }

        for(source in sources) {
            println("Installing $source")
            source.install(workingDirectory)
        }
    }
}
