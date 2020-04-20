package application

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
        addPointerInfo(next, pointerInfo)
    }
}

fun addPointerInfo(identifier: ContractIdentifier, pointerInfo: PointerInfo) {
    ExistingFile(identifier.cacheDescriptorFile).writeText(pointerInfo.toJSONString())
}
