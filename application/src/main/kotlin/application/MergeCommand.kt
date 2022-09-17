package application

import `in`.specmatic.core.Feature
import `in`.specmatic.core.log.logger
import io.swagger.v3.core.util.Yaml
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "merge", description = ["Merge the specified contracts"], mixinStandardHelpOptions = true)
class MergeCommand: Callable<Unit> {
    @CommandLine.Parameters(index = "0..*")
    var contractFiles: List<File> = emptyList()

    @CommandLine.Option(names = ["--merged-contract"])
    var outputFile: File = File("new-contract.yaml")

    override fun call() {
        val contracts: List<Feature> = contractFiles.map {
            try {
                parseContract(it.readText(), it.canonicalPath)
            } catch(e: Throwable) {
                println("Exception loading contract ${it.canonicalPath}")
                e.printStackTrace()
                return
            }
        }

        val mergedFeature = Feature(scenarios = contracts.flatMap { it.scenarios }, name = "New Contract")
        val openApi = mergedFeature.toOpenApi()

        logger.log("Writing merged contract file to ${outputFile.path}")
        outputFile.writeText(Yaml.pretty(openApi))
    }
}
