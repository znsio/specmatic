package application.test

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.utilities.contractFilePathsFrom
import java.util.concurrent.Callable

@Command(name = "manifest",
        mixinStandardHelpOptions = true,
        description = ["Tools for using the manifest"])
class ManifestCommand: Callable<Unit> {
    @Command(name = "fetch", description = [ "Fetch the contracts in the specified manifest" ])
    fun fetch(@Parameters(index = "0", paramLabel = "manifestPath", description = ["Manifest file path"]) path: String, @Parameters(index = "1", description = ["The working directory in which contacts will be checked out"], paramLabel = "workingDirectory") workingDirectory: String) {
        println("Downloading contracts declared in manifest $path into $workingDirectory")
        val contractPaths = contractFilePathsFrom(path, workingDirectory) { source -> source.testContracts + source.stubContracts }
        if(path.isNotEmpty()) {
            println("Contracts downloaded:")

            for(contractPath in contractPaths)
                println(contractPath.prependIndent("  "))
        }
    }

    override fun call() {
        CommandLine(ManifestCommand()).usage(System.out)
    }
}