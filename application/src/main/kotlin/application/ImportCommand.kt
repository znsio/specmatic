package application

import picocli.CommandLine
import picocli.CommandLine.*
import run.qontract.conversions.postmanCollectionToGherkin
import run.qontract.conversions.runTests
import run.qontract.conversions.toFragment
import run.qontract.core.*
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.utilities.parseXML
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.mock.mockFromJSON
import java.io.File
import java.util.concurrent.Callable

@Command(name = "import",
        mixinStandardHelpOptions = true,
        description = ["Converts a files of various formats into their respective $APPLICATION_NAME equivalents"])
class ImportCommand : Callable<Unit> {
    @Command(name="stub")
    fun stub(@Parameters(description = [ "Converts a stub json file to a $APPLICATION_NAME file" ], index = "0") path: String, @Option(names = ["-o", "--output"], description = [ "Write the contract into this file"], required = false) outputFile: String?) {
        val inputFile = File(path)
        val stub = mockFromJSON(jsonStringToValueMap(inputFile.readText()))
        val gherkin = toGherkinFeature(NamedStub("New scenario", stub))

        writeOut(gherkin, outputFile, inputFile, "")
    }

    @Command(name="postman")
    fun postman(@Parameters(description = [ "Converts a postman collection to a $APPLICATION_NAME file" ], index = "0") path: String, @Option(names = ["-o", "--output"], description = [ "Write the contract into this file"], required = false) outputFile: String?) {
        val inputFile = File(path)
        val contracts = postmanCollectionToGherkin(inputFile.readText())

        for(contract in contracts) runTests(contract)

        when (contracts.size) {
            1 -> writeOut(contracts.first().gherkin, outputFile, inputFile, toFragment(contracts.first().baseURLInfo))
            else -> {
                for(contract in contracts) {
                    val (_, gherkin, baseURLInfo, _) = contract
                    writeOut(gherkin, outputFile, inputFile, toFragment(baseURLInfo))
                }
            }
        }
    }

    @Command(name="wsdl")
    fun wsdl(@Parameters(description = [ "Converts a WSDL file to a $APPLICATION_NAME file" ], index = "0") path: String, @Option(names = ["-o", "--output"], description = [ "Write the contract into this file"], required = false) outputFile: String?) {
        val inputFile = File(path)
        val inputFileContent = inputFile.readText()
        val wsdlXML = toXMLNode(parseXML(inputFileContent))
        val contract = WSDL(wsdlXML).convertToGherkin()

        writeOut(contract, outputFile, inputFile, "")
    }

    private fun writeOut(gherkin: String, outputFile: String?, inputFile: File, hostAndPort: String) {
        when (outputFile) {
            null -> {
                if(inputFile.name.endsWith(".wsdl")) {
                    val filename = inputFile.nameWithoutExtension + ".$CONTRACT_EXTENSION"
                    File(filename).writeText(gherkin)
                    println("Written to file $filename")
                }
                else
                    println(gherkin)
            }
            else -> File(outputFile).let {
                val tag = if(hostAndPort.isNotEmpty()) "-${hostAndPort.replace(":", "-")}" else ""

                when {
                    it.isDirectory -> {
                        val dir = it.absoluteFile.parentFile.path.removeSuffix(File.separator)
                        val name = inputFile.nameWithoutExtension
                        val extension = CONTRACT_EXTENSION

                        val outputPath = "$dir${File.separator}$name.$extension"
                        val withTag = fileWithTag(File(outputPath), tag)
                        withTag.writeText(gherkin)
                        println("Written to file ${withTag.path}")
                    }
                    else -> {
                        fileWithTag(it, tag).writeText(gherkin)
                        println("Written to file ${fileWithTag(it, tag).path}")
                    }
                }
            }
        }
    }

    override fun call() {
        CommandLine(ImportCommand()).usage(System.out)
    }
}

private fun fileWithTag(file: File, tag: String): File {
    val taggedFilePath = "${file.absoluteFile.parentFile.path.removeSuffix(File.separator)}${File.separator}${file.nameWithoutExtension}$tag.${file.extension}"
    return File(taggedFilePath)
}

