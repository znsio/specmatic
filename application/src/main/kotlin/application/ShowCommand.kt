package application

import run.qontract.core.versioning.ContractIdentifier
import run.qontract.core.versioning.getRepoProvider
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(name = "show", description = ["Display the specified contract on the console"], mixinStandardHelpOptions = true)
class ShowCommand: Callable<Unit> {
    @CommandLine.Parameters(index = "0", description = ["Name of the contract"])
    var contractName: String = ""

    @CommandLine.Parameters(index = "1", description = ["Version of the contract"])
    var version: Int = 0

    override fun call() {
        val identifier = ContractIdentifier(contractName, version)
        val repoProvider = getRepoProvider(identifier)
        println(repoProvider.getContractData(identifier))
    }
}