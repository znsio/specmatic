import picocli.CommandLine
import run.qontract.core.utilities.loadSourceDataFromManifest
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "install", description = ["Clone the git repositories declared in the manifest"], mixinStandardHelpOptions = true)
class InstallCommand: Callable<Unit> {
    override fun call() {
        val userHome = File(System.getProperty("user.home"))
        val workingDirectory = userHome.resolve(".qontract/repos")

        val sources = loadSourceDataFromManifest("./qontract.json")

        for(source in sources) {
            println("Installing $source")
            source.install(workingDirectory)
        }
    }
}
