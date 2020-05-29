package application

import application.versioning.commands.RepoCommand
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.ContractBehaviour
import run.qontract.core.pattern.ContractException
import run.qontract.core.testBackwardCompatibility2
import run.qontract.core.toGherkinString
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.utilities.readFile
import run.qontract.mock.mockFromJSON
import java.io.File
import java.util.concurrent.Callable

@Command(name = "import",
        mixinStandardHelpOptions = true,
        description = ["Converts a files of various formats into their respective Qontract eqiuvalents"])
class ImportCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Stub file path"])
    lateinit var path: String

    @Command(name="stub")
    fun stub(@Parameters(description = [ "Converts a stub json file to a Qontract file" ], index = "0") path: String) {
        val mock = mockFromJSON(jsonStringToValueMap((File(path).readText())))
        println(toGherkinString(mock))
    }

    override fun call() {
        CommandLine(RepoCommand()).usage(System.out)
    }
}
