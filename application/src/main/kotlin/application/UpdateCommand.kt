package application

import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "update", mixinStandardHelpOptions = true)
class UpdateCommand: Callable<Unit> {
    @CommandLine.Parameters(index = "0", descriptionKey = "contractPath")
    var contractPath: String = ""

    @CommandLine.Parameters(index = "1", descriptionKey = "version")
    var version: Int = 0

    override fun call() {
        val identifier = ContractIdentifier(File(contractPath).name.removeSuffix(".contract"), version)
        val contractFile = File(contractPath)

        if(!identifier.cacheDescriptorFile.exists()) {
            println("Can't find ${identifier.displayableString}")
            return
        }

        val repoProvider = getRepoProvider(identifier)
        val results = repoProvider.testBackwardCompatibility(identifier, contractFile)
        if(!results.success()) {
            println("The new contract is not backward compatible with the older one.")
            println()
            println(results.report())
            return
        }

        repoProvider.updateContract(identifier, contractFile)
    }
}
