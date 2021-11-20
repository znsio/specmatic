@file:JvmName("BundleCommand_Jvm")

package application

import `in`.specmatic.core.log.StringLog
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.utilities.exitWithMessage
import io.swagger.util.Yaml
import org.springframework.beans.factory.annotation.Autowired
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable

@CommandLine.Command(name = "gherkin-to-openapi",
        mixinStandardHelpOptions = true,
        description = ["Converts a Gherkin file to OpenAPI"])
class GherkinToOpenAPICommand : Callable<Unit> {
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

        logger.keepReady(StringLog("Converting $contractPath"))

        if(!fileOperations.isFile(contractPath))
            exitWithMessage("$contractPath is not a file")

        try {
            val openAPI = parseGherkinStringToFeature(fileOperations.read(contractPath)).toOpenApi()
            val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
            val contractFile = File(contractPath)
            val openAPIFile: File =
                contractFile.canonicalFile.parentFile.resolve(contractFile.nameWithoutExtension + ".yaml")
            openAPIFile.writeText(openAPIYaml)
        } catch(e: Throwable) {
            logger.log("# Error converting file $contractPath")
            logger.log(e)
        }
    }
}
