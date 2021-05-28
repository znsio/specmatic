package application

import picocli.CommandLine
import picocli.CommandLine.*
import `in`.specmatic.conversions.postmanCollectionToGherkin
import `in`.specmatic.conversions.runTests
import `in`.specmatic.conversions.toFragment
import `in`.specmatic.core.*
import `in`.specmatic.core.git.Verbose
import `in`.specmatic.core.git.log
import `in`.specmatic.core.git.logException
import `in`.specmatic.core.utilities.jsonStringToValueMap
import `in`.specmatic.core.utilities.parseXML
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.mock.mockFromJSON
import java.io.File
import java.util.concurrent.Callable

@Command(name = "import",
        mixinStandardHelpOptions = true,
        description = ["Converts a files of various formats into their respective $APPLICATION_NAME equivalents"])
class ImportCommand : Callable<Unit> {
    @Command(name="stub")
    fun stub(
        @Parameters(description = ["Converts a stub json file to a $APPLICATION_NAME file"], index = "0") path: String,
        @Option(
            names = ["-o", "--output"],
            description = ["Write the contract into this file"],
            required = false
        ) userSpecifiedOutFile: String?,
        @Option(names = ["-V", "--verbose"], required = false, defaultValue = "false") verbose: Boolean
    ) {
        if(verbose)
            log = Verbose

        logException {
            val inputFile = File(path)
            val stub = mockFromJSON(jsonStringToValueMap(inputFile.readText()))
            val gherkin = toGherkinFeature(NamedStub("New scenario", stub))

            val outFile = userSpecifiedOutFile ?: "${inputFile.nameWithoutExtension}.$CONTRACT_EXTENSION"

            writeOut(gherkin, outFile)
        }
    }

    @Command(name="postman")
    fun postman(
        @Parameters(
            description = ["Converts a postman collection to a $APPLICATION_NAME file"],
            index = "0"
        ) path: String,
        @Option(
            names = ["-o", "--output"],
            description = ["Write the contract into this file"],
            required = false
        ) userSpecifiedOutPath: String?,
        @Option(names = ["-V", "--verbose"], required = false, defaultValue = "false") verbose: Boolean
    ) {
        if(verbose)
            log = Verbose

        logException {
            val inputFile = File(path)
            val contracts = postmanCollectionToGherkin(inputFile.readText())

            for (contract in contracts) runTests(contract)

            when (contracts.size) {
                1 -> {
                    val outPath = userSpecifiedOutPath ?: "${inputFile.nameWithoutExtension}.${CONTRACT_EXTENSION}"

                    writeOut(contracts.first().gherkin, outPath, toFragment(contracts.first().baseURLInfo))
                }
                else -> {
                    for (contract in contracts) {
                        val (_, gherkin, baseURLInfo, _) = contract
                        val outFilePath = userSpecifiedOutPath ?: "${inputFile.nameWithoutExtension}.${CONTRACT_EXTENSION}"
                        writeOut(gherkin, outFilePath, toFragment(baseURLInfo))
                    }
                }
            }
        }
    }

    @Command(name="wsdl")
    fun wsdl(
        @Parameters(description = ["Converts a WSDL file to a $APPLICATION_NAME file"], index = "0") path: String,
        @Option(
            names = ["-o", "--output"],
            description = ["Write the contract into this file"],
            required = false
        ) userSpecifiedOutFile: String?,
        @Option(names = ["-V", "--verbose"], required = false, defaultValue = "false") verbose: Boolean
    ) {
        if(verbose)
            log = Verbose

        logException {
            val inputFile = File(path)
            val inputFileContent = inputFile.readText()
            val wsdlXML = toXMLNode(parseXML(inputFileContent))
            val contract = WSDL(wsdlXML).convertToGherkin()

            val outFile = userSpecifiedOutFile ?: "${inputFile.nameWithoutExtension}.$CONTRACT_EXTENSION"

            writeOut(contract, outFile)
        }
    }

    private fun writeOut(gherkin: String, outputFilePath: String, hostAndPort: String? = null) {
        val outputFile = File(outputFilePath)

        val tag = if(hostAndPort != null) "-${hostAndPort.replace(":", "-")}" else ""

        fileWithTag(outputFile, tag).writeText(gherkin)
        log.message("Written to file ${fileWithTag(outputFile, tag).path}")
    }

    override fun call() {
        CommandLine(ImportCommand()).usage(System.out)
    }
}

private fun fileWithTag(file: File, tag: String): File {
    val taggedFilePath = "${file.absoluteFile.parentFile.path.removeSuffix(File.separator)}${File.separator}${file.nameWithoutExtension}$tag.${file.extension}"
    return File(taggedFilePath)
}

