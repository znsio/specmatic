@file:JvmName("BundleCommand_Jvm")

package application

import `in`.specmatic.core.YAML
import `in`.specmatic.core.log.HeadingLog
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.utilities.exitWithMessage
import io.swagger.v3.core.util.Yaml
import org.springframework.beans.factory.annotation.Autowired
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(name = "to-openapi",
        mixinStandardHelpOptions = true,
        description = ["Converts a Gherkin file to OpenAPI"])
class ToOpenAPICommand : Callable<Unit> {
    @CommandLine.Parameters(description = ["Path in which to create the bundle"])
    lateinit var contractPath: String

    @CommandLine.Option(names = ["--debug"])
    var verbose: Boolean = false

    @Autowired
    lateinit var fileOperations: FileOperations

    override fun call() {
        if(verbose) {
            logger = Verbose()
        }

        logger.keepReady(HeadingLog(contractPath))

        if(!fileOperations.isFile(contractPath)) {
            exitWithMessage("$contractPath is not a file")
        }

        try {
            val gherkinContract = parseGherkinStringToFeature(fileOperations.read(contractPath))
            val openAPI = gherkinContract.toOpenApi()
            val openAPIYaml = Yaml.pretty(openAPI)
            val contractFile = File(contractPath)
            val openAPIFile: File =
                contractFile.canonicalFile.parentFile.resolve("${contractFile.nameWithoutExtension}.$YAML")
            openAPIFile.writeText(openAPIYaml)
        } catch(e: Throwable) {
            logger.log(e)
            exitProcess(1)
        }
    }
}
