package application.versioning.commands

import run.qontract.core.versioning.ContractIdentifier
import run.qontract.core.versioning.PointerInfo
import run.qontract.core.versioning.findLatestVersion
import run.qontract.core.versioning.getRepoProvider
import picocli.CommandLine
import run.qontract.core.CONTRACT_EXTENSION
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "increment", description = ["Store the contract as a new version, which is one greater than the highest available version."], mixinStandardHelpOptions = true)
class IncrementCommand: Callable<Unit> {
    @CommandLine.Parameters(index = "0", description = ["path to the contract"])
    var contractPath: String = ""

    override fun call() {
        val name = File(contractPath).name.removeSuffix(".$CONTRACT_EXTENSION")
        val version = findLatestVersion(name)

        if(version == null) {
            println("There are no prior versions. This contract is completely new")
            return
        }

        val latestInCache = ContractIdentifier(name, version)
        val next = latestInCache.incrementedVersion()

        val contractFile = File(contractPath)

        val repoProvider = getRepoProvider(latestInCache)
        val pointerInfo: PointerInfo = repoProvider.addContract(next, contractFile)
        createPointerFile(next, pointerInfo)
    }
}

fun createPointerFile(identifier: ContractIdentifier, pointerInfo: PointerInfo) {
    ExistingFile(identifier.getCacheDescriptorFile()).writeText(pointerInfo.toJSONString())
}
