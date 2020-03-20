package application

import run.qontract.core.ContractBehaviour
import run.qontract.core.Suggestions
import run.qontract.core.utilities.readFile
import run.qontract.fake.ContractFake
import run.qontract.test.HttpClient
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@Command(name = "test-deprecated", version = ["0.1.0"],
        mixinStandardHelpOptions = true)
class TestCommandDeprecated : Callable<Void> {
    lateinit var contractFake: ContractFake

    @Option(names = ["--path"], description = ["Contract location"], required = true)
    lateinit var path: String

    @Option(names = ["--host"], description = ["Host"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port"], defaultValue = "9000")
    lateinit var port: Integer

    @Option(names = ["--suggestions"], description = ["run.qontract.core.Suggestions location"], defaultValue = "")
    lateinit var suggestionsPath: String

    @Command
    fun run() {
        val contractGherkin = readFile(path)
        val contractBehaviour = ContractBehaviour(contractGherkin)
        if (suggestionsPath.isEmpty()) {
            val executionInfo = contractBehaviour.executeTests(HttpClient("http://$host:$port"))
            executionInfo.print()
        } else {
            val suggestionsGherkin = readFile(suggestionsPath)
            val suggestions = Suggestions(suggestionsGherkin).scenarios
            val executionInfo = contractBehaviour.executeTests(suggestions, HttpClient("http://$host:$port"))
            executionInfo.print()
        }
    }

    override fun call(): Void? {
        CommandLine(StubCommand()).usage(System.out)
        return null
    }

}
