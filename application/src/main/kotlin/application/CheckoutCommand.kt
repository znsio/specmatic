package application

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Paths
import java.util.concurrent.Callable

@Command(name = "checkout", mixinStandardHelpOptions = true)
class CheckoutCommand: Callable<Unit> {
    @Parameters(index = "0", descriptionKey = "contractName")
    var contractName: String = ""

    @Parameters(index = "1", descriptionKey = "version")
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
    return FileExists("${currentWorkingDir()}/${contractIdentifier.contractName}.contract")
}
