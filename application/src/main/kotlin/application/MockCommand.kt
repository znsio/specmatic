package application

import picocli.CommandLine
import picocli.CommandLine.HelpCommand
import run.qontract.core.utilities.readFile
import run.qontract.mock.ContractMock
import java.util.concurrent.Callable

@CommandLine.Command(name = "mock", description = ["Runs contract mocks"], subcommands = [HelpCommand::class])
class MockCommand : Callable<Void?> {
    @CommandLine.Command
    @Throws(Throwable::class)
    fun file(@CommandLine.Parameters(description = ["Path to file containing contract gherkin"]) path: String) {
        val spec = readFile(path)
        ContractMock.fromGherkin(spec, 9000).use { mock ->
            mock.start()
            mock.waitUntilClosed()
        }
    }

    override fun call(): Void? {
        CommandLine(MockCommand()).usage(System.out)
        return null
    }
}