package application

import application.versioning.ContractIdentifier
import application.versioning.PointerInfo
import application.versioning.findLatestVersion
import application.versioning.getRepoProvider
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "increment", mixinStandardHelpOptions = true)
class IncrementCommand: Callable<Unit> {
    @CommandLine.Parameters(index = "0", descriptionKey = "contractPath")
    var contractPath: String = ""

    override fun call() {
        val name = File(contractPath).name.removeSuffix(".contract")
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
