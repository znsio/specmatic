package application

import run.qontract.core.utilities.*
import run.qontract.core.utilities.BrokerClient.readFromURL
import picocli.CommandLine
import picocli.CommandLine.*
import run.qontract.core.*
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "contract", description = ["Manages contracts"], subcommands = [HelpCommand::class])
class ContractCommand : Callable<Void?> {
    @Command(description = ["Publish a contract to the broker"])
    @Throws(IOException::class)
    fun publish(@Option(names = ["--majorVersion"], required = true, description = ["Major version of the contract"], paramLabel = "<major version>") majorVersion: String?, @Option(names = ["--name"], description = ["Name of the contract"], paramLabel = "<name>") contractName: String?, @Option(names = ["--file"], description = ["Path to the file containing the contract"], paramLabel = "<file path>") contractFilePath: String?) {
        if (contractFilePath != null && contractName == null ||
                contractFilePath == null && contractName != null) {
            val message = "contract-file-path and contract-name go together for publishing contracts. Specify both or neither."
            println(message)
            return
        }
        val provider = contractName ?: ComponentManifest().componentName!!
        val contract = getServiceContract(contractFilePath)
        val jsonMessage = mutableMapOf<String, Any?>()
        jsonMessage["provider"] = provider
        jsonMessage["contract"] = contract
        if (majorVersion != null) {
            jsonMessage["majorVersion"] = Integer.valueOf(majorVersion)
        }
        writeToAPI(io.ktor.http.HttpMethod.Put, "$brokerURL/contracts", jsonMessage)
    }

    @Command(description = ["Show all version numbers of a contract available with the broker"])
    @Throws(IOException::class)
    fun list(@Option(names = ["--name"], description = ["Name of the contracts whose versions should be listed"], paramLabel = "<name>", required = true) contractName: String) {
        val url = "$brokerURL/contract-versions?provider=$contractName"
        val versionsResponse = readFromURL(url)
        val versions = versionsResponse["versions"] as List<Any?>
        println("Versions of $contractName")
        for (i in versions) {
            val version = i as List<Int>
            val majorVersion = version[0]
            val minorVersion = version[1]
            println("$majorVersion.$minorVersion")
        }
    }

    @Command(description = ["Fetch a contract from the broker and show it"])
    fun show(@Option(names = ["--name"], description = ["Name of the contract to show"], paramLabel = "<name>", required = true) contractName: String, @Option(names = ["--version"], description = ["Version of the contract to show"], paramLabel = "<version>") versionSpec: String?) {
        val version = toVersion(versionSpec)
        val response = readFromAPI(brokerURL + "/contracts?provider=" + contractName + version.toQueryParams())
        val jsonObject = jsonStringToMap(response)
        val majorVersion = jsonObject["majorVersion"].toString()
        val minorVersion = jsonObject["minorVersion"].toString()
        val spec = jsonObject["spec"].toString()
        val message = "Version: " + majorVersion + "." + minorVersion + "\n" +
                spec + "\n"
        println(message)
    }

    @Command(description = ["Test backward compatibility of a new contract"] )
    fun compare(@Option(names = ["--older"], description = ["Name of the older contract"], paramLabel = "<older file path>", required = true) olderFilePath: String, @Option(names = ["--newer"], description = ["Name of the newer contract"], paramLabel = "<newer file path>", required=true) newerFilePath: String) {
        val older = ContractBehaviour(File(olderFilePath).readText())
        val newer = ContractBehaviour(File(newerFilePath).readText())
        val results = testBackwardCompatibility(older, newer)

        if(results.failureCount > 0) {
            println(results.report())
            exitProcess(1)
        } else {
            println("Older and newer contracts are compatible.")
        }
    }

    override fun call(): Void? {
        CommandLine(ContractCommand()).usage(System.out)
        return null
    }
}