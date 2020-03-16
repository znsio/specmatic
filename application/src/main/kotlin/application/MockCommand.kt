package application

import run.qontract.core.toVersion
import run.qontract.core.utilities.brokerURL
import run.qontract.core.utilities.jsonStringToMap
import run.qontract.core.utilities.readFile
import run.qontract.core.utilities.readFromAPI
import run.qontract.mock.ContractMock
import picocli.CommandLine
import picocli.CommandLine.HelpCommand
import java.util.concurrent.Callable

@CommandLine.Command(name = "mock", description = ["Runs contract mocks"], subcommands = [HelpCommand::class])
class MockCommand : Callable<Void?> {
    @CommandLine.Command
    @Throws(Throwable::class)
    fun version(@CommandLine.Parameters(description = ["Name of the contract to mock out"], paramLabel = "<name>") contractName: String, @CommandLine.Parameters(description = ["Dot separated version (e.g. 2, 2.0, 1.1) of the contract"], paramLabel = "<version>") versionSpec: String?) {
        val version = toVersion(versionSpec)
        val response = readFromAPI(brokerURL + "/contracts?provider=" + contractName + version.toQueryParams())
        val jsonObject = jsonStringToMap(response)
        val spec = jsonObject["spec"] as String
        ContractMock.fromGherkin(spec, 9000).use { mock -> mock.waitUntilClosed() }
    }

    @CommandLine.Command
    @Throws(Throwable::class)
    fun file(@CommandLine.Parameters(description = ["Path to file containing contract gherkin"], paramLabel = "<path>") path: String) {
        val spec = readFile(path)
        ContractMock.fromGherkin(spec, 9000).use { mock -> mock.waitUntilClosed() }
    }

    override fun call(): Void? {
        CommandLine(MockCommand()).usage(System.out)
        return null
    }
}