package application

import application.versioning.commands.RepoCommand
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.conversions.postmanCollectionToGherkin
import run.qontract.core.NamedStub
import run.qontract.core.toGherkinFeature
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.mock.mockFromJSON
import java.io.File
import java.util.concurrent.Callable

@Command(name = "import",
        mixinStandardHelpOptions = true,
        description = ["Converts a files of various formats into their respective Qontract eqiuvalents"])
class ImportCommand : Callable<Unit> {
    @Command(name="stub")
    fun stub(@Parameters(description = [ "Converts a stub json file to a Qontract file" ], index = "0") path: String) {
        val stub = mockFromJSON(jsonStringToValueMap((File(path).readText())))
        println(toGherkinFeature(NamedStub("New scenario", stub)))
    }

    @Command(name="postman")
    fun postman(@Parameters(description = [ "Converts a postman collection to a Qontract file" ], index = "0") path: String) {
        val (gherkin, _) = postmanCollectionToGherkin(File(path).readText())
        println(gherkin)
    }

    override fun call() {
        CommandLine(RepoCommand()).usage(System.out)
    }
}
