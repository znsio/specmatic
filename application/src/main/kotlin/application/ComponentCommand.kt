package application

import run.qontract.core.ComponentManifest
import run.qontract.core.utilities.*
import picocli.CommandLine
import picocli.CommandLine.HelpCommand
import java.io.IOException
import java.util.concurrent.Callable

@CommandLine.Command(name = "component", description = ["Manages components"], subcommands = [HelpCommand::class])
class ComponentCommand : Callable<Void?> {
    @CommandLine.Command
    @Throws(IOException::class)
    fun publish(@CommandLine.Option(names = ["--environment"], description = ["Environment in which to register the component"], paramLabel = "<environment name>", required = true) environment: String) {
        val componentManifest = ComponentManifest()
        val serviceName = componentManifest.componentName
        val url = "$brokerURL/environment/$environment"
        val contractInfo = mutableMapOf<String, Any?>()
        contractInfo["content"] = getServiceContract(contractFilePath)

        val jsonMessage = mutableMapOf<String, Any?>()
        jsonMessage["service"] = serviceName
        jsonMessage["contract"] = contractInfo

        loadDependencyInfo()?.let { jsonMessage["dependencies"] = it }

        writeToAPI(io.ktor.http.HttpMethod.Post, url, jsonMessage)
    }

    override fun call(): Void? {
        CommandLine(ComponentCommand()).usage(System.out)
        return null
    }
}