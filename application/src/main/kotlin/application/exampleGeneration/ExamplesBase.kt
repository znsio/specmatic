package application.exampleGeneration

import io.specmatic.core.DICTIONARY_FILE_SUFFIX
import io.specmatic.core.EXAMPLES_DIR_SUFFIX
import io.specmatic.core.SPECMATIC_STUB_DICTIONARY
import io.specmatic.core.log.*
import picocli.CommandLine
import java.io.File
import java.util.concurrent.Callable

abstract class ExamplesBase<Feature, Scenario>(open val featureStrategy: ExamplesFeatureStrategy<Feature, Scenario>) : Callable<Int> {
    protected abstract var contractFile: File?

    @CommandLine.Option(names = ["--filter-name"], description = ["Use only APIs with this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NAME}")
    private var filterName: String = ""

    @CommandLine.Option(names = ["--filter-not-name"], description = ["Use only APIs which do not have this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}")
    private var filterNotName: String = ""

    @CommandLine.Option(names = ["--debug"], description = ["Debug logs"])
    private var verbose = false

    override fun call(): Int {
        contractFile?.let {
            if (!it.exists()) {
                logger.log("Contract file does not exist: ${it.absolutePath}")
                return 1
            }

            if (it.extension !in featureStrategy.contractFileExtensions) {
                logger.log("Invalid Contract file ${it.path} - File extension must be one of ${featureStrategy.contractFileExtensions.joinToString()}")
                return 1
            }
        }

        val exitCode = execute(contractFile)
        return exitCode
    }

    // HOOKS
    abstract fun execute(contract: File?): Int

    // HELPER METHODS
    fun configureLogger(verbose: Boolean) {
        val logPrinters = listOf(ConsolePrinter)

        logger = if (verbose)
            Verbose(CompositePrinter(logPrinters))
        else
            NonVerbose(CompositePrinter(logPrinters))
    }

    fun getFilteredScenarios(feature: Feature, extensive: Boolean = false): List<Scenario> {
        val scenarioFilter = ScenarioFilter(filterName, filterNotName)
        val scenarios = featureStrategy.getScenariosFromFeature(feature, extensive)
        return getFilteredScenarios(scenarios, scenarioFilter)
    }

    fun getExamplesDirectory(contractFile: File): File {
        val examplesDirectory = contractFile.canonicalFile.parentFile.resolve("${contractFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX")
        if (!examplesDirectory.exists()) {
            logger.log("Creating examples directory: $examplesDirectory")
            examplesDirectory.mkdirs()
        }
        return examplesDirectory
    }

    fun getExternalExampleFiles(examplesDirectory: File): List<File> {
        return examplesDirectory.walk().filter {
            it.isFile && it.extension in featureStrategy.exampleFileExtensions
        }.toList()
    }

    fun logSeparator(length: Int, separator: String = "-") {
        logger.log(separator.repeat(length))
    }

    fun logFormattedOutput(header: String, summary: String, note: String) {
        val maxLength = maxOf(summary.length, note.length, 50).let { it + it % 2 }
        val headerSidePadding = (maxLength - 2 - header.length) / 2

        val paddedHeaderLine = "=".repeat(headerSidePadding) + " $header " + "=".repeat(headerSidePadding)
        val paddedSummaryLine = summary.padEnd(maxLength)
        val paddedNoteLine = note.padEnd(maxLength)

        logger.log("\n$paddedHeaderLine")
        logger.log(paddedSummaryLine)
        logger.log("=".repeat(maxLength))
        logger.log(paddedNoteLine)
    }

    fun updateDictionaryFile(dictFileFromArgs: File? = null, contract: File? = contractFile): File? {
        val dictFile = when(dictFileFromArgs != null) {
            true -> {
                dictFileFromArgs.takeIf { it.exists() } ?: throw Exception("Dictionary file does not exist: ${dictFileFromArgs.absolutePath}")
            }
            false -> {
                val dictInContractFolder = contract?.parentFile?.resolve("${contract.nameWithoutExtension}$DICTIONARY_FILE_SUFFIX")
                val dictFileInCurDir = File(".").resolve("${contractFile?.nameWithoutExtension}$DICTIONARY_FILE_SUFFIX")

                dictInContractFolder?.takeIf { it.exists() } ?: dictFileInCurDir.takeIf { it.exists() }
            }
        }

        dictFile?.let {
            logger.log("Using Dictionary file: ${it.absolutePath}")
            System.setProperty(SPECMATIC_STUB_DICTIONARY, it.absolutePath)
        }

        return dictFile
    }

    private fun getFilteredScenarios(scenarios: List<Scenario>, scenarioFilter: ScenarioFilter): List<Scenario> {
        val filteredScenarios = scenarios
            .filterScenarios(scenarioFilter.filterNameTokens, shouldMatch = true)
            .filterScenarios(scenarioFilter.filterNotNameTokens, shouldMatch = false)

        if (filteredScenarios.isEmpty()) {
            logger.log("Note: All examples were filtered out by the filter expression")
        }

        return filteredScenarios
    }

    private fun List<Scenario>.filterScenarios(tokens: Set<String>, shouldMatch: Boolean): List<Scenario> {
        if (tokens.isEmpty()) return this

        return this.filter {
            val description = featureStrategy.getScenarioDescription(it)
            tokens.any { token ->
                description.contains(token)
            } == shouldMatch
        }
    }
}

interface ExamplesFeatureStrategy<Feature, Scenario> {
    val exampleFileExtensions: Set<String>
    val contractFileExtensions: Set<String>

    fun contractFileToFeature(contractFile: File): Feature

    fun getScenariosFromFeature(feature: Feature, extensive: Boolean) : List<Scenario>

    fun getScenarioDescription(scenario: Scenario): String
}

class ScenarioFilter(filterName: String, filterNotName: String) {
    val filterNameTokens = filterToTokens(filterName)
    val filterNotNameTokens = filterToTokens(filterNotName)

    private fun filterToTokens(filterValue: String): Set<String> {
        if (filterValue.isBlank()) return emptySet()
        return filterValue.split(",").map { it.trim() }.toSet()
    }
}
