package application.versioning.commands

import run.qontract.core.versioning.ContractIdentifier
import run.qontract.core.versioning.getRepoProvider
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Paths
import java.util.concurrent.Callable

@Command(name = "checkout", mixinStandardHelpOptions = true, description = ["Create a file in the current directory with the specified contract"])
class CheckoutCommand: Callable<Unit> {
    @Parameters(index = "0", description = ["Name of the contract"])
    var contractName: String = ""

    @Parameters(index = "1", description = ["Version of the contract"])
    var version: Int = 0

    override fun call() {
        val identifier = ContractIdentifier(contractName, version)
        val newContractFile = newContractFile(identifier)

        newContractFile.ifExists {
            println("${it.path} already exists.")
        }

        newContractFile.ifDoesNotExist {
            println("Writing contract ${identifier.displayableString} to file ${it.path}")
            val repoProvider = getRepoProvider(identifier)
            it.writeText(repoProvider.readContract(identifier))
        }
    }
}

fun currentWorkingDir(): String = Paths.get("").toAbsolutePath().toString()

fun newContractFile(contractIdentifier: ContractIdentifier): FileExists {
    return FileExists("${currentWorkingDir()}/${contractIdentifier.name}.contract")
}
