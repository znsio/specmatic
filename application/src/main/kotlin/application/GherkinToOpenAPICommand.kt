@file:JvmName("BundleCommand_Jvm")

package application

import `in`.specmatic.core.information
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.utilities.exitWithMessage
import org.springframework.beans.factory.annotation.Autowired
import picocli.CommandLine
import java.util.concurrent.Callable
import io.swagger.util.Yaml
import java.io.File

@CommandLine.Command(name = "gherkin-to-openapi",
        mixinStandardHelpOptions = true,
        description = ["Converts a Gherkin file to OpenAPI"])
class GherkinToOpenAPICommand : Callable<Unit> {
    @CommandLine.Parameters(description = ["Path in which to create the bundle"])
    lateinit var contractPath: String

    @Autowired
    lateinit var qontractConfig: QontractConfig

    @Autowired
    lateinit var fileOperations: FileOperations

    override fun call() {
        if(!fileOperations.isFile(contractPath))
            exitWithMessage("$contractPath is not a file")

        val openAPI = parseGherkinStringToFeature(fileOperations.read(contractPath)).toOpenApi()
        val openAPIYaml = Yaml.mapper().writeValueAsString(openAPI)
        val contractFile = File(contractPath)
        val openAPIFile: File = contractFile.canonicalFile.parentFile.resolve(contractFile.nameWithoutExtension + ".yaml")
        openAPIFile.writeText(openAPIYaml)
    }
}
