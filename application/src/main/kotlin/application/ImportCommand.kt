package application

import picocli.CommandLine.*
import `in`.specmatic.conversions.postmanCollectionToGherkin
import `in`.specmatic.conversions.runTests
import `in`.specmatic.conversions.toFragment
import `in`.specmatic.core.*
import `in`.specmatic.core.git.Verbose
import `in`.specmatic.core.git.information
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
        description = ["Converts a $APPLICATION_NAME stub, Postman or WSDL file into a $APPLICATION_NAME spec file"])
class ImportCommand : Callable<Int> {
    @Parameters(description = ["File to convert"], index = "0")
    lateinit var path: String

    @Option(names = ["--output"], description = ["Write the contract into this file"], required = false)
    var userSpecifiedOutFile: String? = null

    @Option(names = ["--verbose"], required = false, defaultValue = "false")
    var verbose: Boolean = false

    override fun call(): Int {
        if(verbose)
            information = Verbose

        return logException {
            when {
                path.endsWith(".postman_collection.json") ->
                    convertPostman(path, userSpecifiedOutFile)
                path.endsWith(".json") ->
                    convertStub(path, userSpecifiedOutFile)
                path.endsWith(".wsdl") ->
                    convertWSDL(path, userSpecifiedOutFile)
                else -> {
                    throw Exception("File type not recognized. Support exceptions include .postman_collection.json (Postman), .json ($APPLICATION_NAME stub), .wsdl (WSDL)")
                }
            }
        }
    }
}

fun convertStub(path: String, userSpecifiedOutFile: String?) {
    val inputFile = File(path)
    val stub = mockFromJSON(jsonStringToValueMap(inputFile.readText()))
    val gherkin = toGherkinFeature(NamedStub("New scenario", stub))

    val outFile = userSpecifiedOutFile ?: "${inputFile.nameWithoutExtension}.$CONTRACT_EXTENSION"

    writeOut(gherkin, outFile)
}

fun convertPostman(path: String, userSpecifiedOutPath: String?) {
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

fun convertWSDL(path: String, userSpecifiedOutFile: String?) {
    val inputFile = File(path)
    val inputFileContent = inputFile.readText()
    val wsdlXML = toXMLNode(parseXML(inputFileContent))
    val contract = WSDL(wsdlXML, path).convertToGherkin()

    val outFile = userSpecifiedOutFile ?: "${inputFile.nameWithoutExtension}.$CONTRACT_EXTENSION"

    writeOut(contract, outFile)
}

private fun writeOut(gherkin: String, outputFilePath: String, hostAndPort: String? = null) {
    val outputFile = File(outputFilePath)

    val tag = if(hostAndPort != null) "-${hostAndPort.replace(":", "-")}" else ""

    fileWithTag(outputFile, tag).writeText(gherkin)
    information.forTheUser("Written to file ${fileWithTag(outputFile, tag).path}")
}

private fun fileWithTag(file: File, tag: String): File {
    val taggedFilePath = "${file.absoluteFile.parentFile.path.removeSuffix(File.separator)}${File.separator}${file.nameWithoutExtension}$tag.${file.extension}"
    return File(taggedFilePath)
}
