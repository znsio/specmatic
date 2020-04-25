package application.versioning.commands

import run.qontract.core.versioning.ContractIdentifier
import run.qontract.core.versioning.getRepoProvider
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "update", description = ["Check the new contract for backward compatibility with the specified version, then overwrite the old one with it."], mixinStandardHelpOptions = true)
class UpdateCommand: Callable<Unit> {
    @CommandLine.Parameters(index = "0", descriptionKey = "contractPath")
    var contractPath: String = ""

    @CommandLine.Parameters(index = "1", descriptionKey = "version")
    var version: Int = 0

    override fun call() {
        val identifier = ContractIdentifier(File(contractPath).name.removeSuffix(".contract"), version)
        val contractFile = File(contractPath)

        if(!identifier.getCacheDescriptorFile().exists()) {
            println("Can't find ${identifier.displayableString}")
            return
        }

        val repoProvider = getRepoProvider(identifier)
        val results = repoProvider.testBackwardCompatibility(identifier, contractFile)
        if(!results.success()) {
            println("The new contract is not backward compatible with the older one.")
            return
        }

        repoProvider.updateContract(identifier, contractFile)
    }
}
