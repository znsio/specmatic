package application

import application.versioning.commands.RepoCommand
import picocli.CommandLine
import picocli.CommandLine.*
import run.qontract.conversions.postmanCollectionToGherkin
import run.qontract.core.QONTRACT_EXTENSION
import run.qontract.core.NamedStub
import run.qontract.core.toGherkinFeature
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.mock.mockFromJSON
import java.io.File
import java.util.concurrent.Callable

@Command(name = "import",
        mixinStandardHelpOptions = true,
        description = ["Converts a files of various formats into their respective Qontract equivalents"])
class ImportCommand : Callable<Unit> {
    @Command(name="stub")
    fun stub(@Parameters(description = [ "Converts a stub json file to a Qontract file" ], index = "0") path: String, @Option(names = ["-o", "--outputFile"], description = [ "Write the contract into this file"], required = false) outputFile: String?) {
        val inputFile = File(path)
        val stub = mockFromJSON(jsonStringToValueMap(inputFile.readText()))
        val gherkin = toGherkinFeature(NamedStub("New scenario", stub))

        spewOut(gherkin, outputFile, inputFile)
    }

    @Command(name="postman")
    fun postman(@Parameters(description = [ "Converts a postman collection to a Qontract file" ], index = "0") path: String, @Option(names = ["-o", "--outputFile"], description = [ "Write the contract into this file"], required = false) outputFile: String?) {
        val inputFile = File(path)
        val (gherkin, _) = postmanCollectionToGherkin(inputFile.readText())

        spewOut(gherkin, outputFile, inputFile)
    }

    private fun spewOut(gherkin: String, outputFile: String?, inputFile: File) {
        when (outputFile) {
            null -> println(gherkin)
            else -> File(outputFile).let {
                when {
                    it.isFile || !it.exists() -> {
                        it.writeText(gherkin)
                        println("Written to file ${it.path}")
                    }
                    it.isDirectory -> {
                        val dir = it.path.removeSuffix(File.pathSeparator)
                        val name = inputFile.nameWithoutExtension
                        val extension = QONTRACT_EXTENSION

                        val outputPath = "$dir/$name.$extension"
                        File(outputPath).writeText(gherkin)
                        println("Written to file $outputPath")
                    }
                }
            }
        }
    }

    override fun call() {
        CommandLine(RepoCommand()).usage(System.out)
    }
}
