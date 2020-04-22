package application

import application.versioning.ContractIdentifier
import application.versioning.getRepoProvider
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(name = "show", mixinStandardHelpOptions = true)
class ShowCommand: Callable<Unit> {
    @CommandLine.Parameters(index = "0", descriptionKey = "contractName")
    var contractName: String = ""

    @CommandLine.Parameters(index = "1", descriptionKey = "version")
    var version: Int = 0

    override fun call() {
        val identifier = ContractIdentifier(contractName, version)
        val repoProvider = getRepoProvider(identifier)
        println(repoProvider.getContractData(identifier))
    }
}