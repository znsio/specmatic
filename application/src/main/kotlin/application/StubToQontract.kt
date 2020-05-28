package application

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

@Command(name = "stubToQontract",
        mixinStandardHelpOptions = true,
        description = ["Converts a stub json file into a Qontract"])
class StubToQontractCommand : Callable<Unit> {
    @Parameters(index = "0", description = ["Stub file path"])
    lateinit var path: String

    override fun call() {
        val mock = mockFromJSON(jsonStringToValueMap((File(path).readText())))
        println(toGherkinString(mock))
    }
}
