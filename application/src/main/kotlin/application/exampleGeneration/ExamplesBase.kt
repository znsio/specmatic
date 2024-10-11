package application.exampleGeneration

import io.specmatic.core.DICTIONARY_FILE_SUFFIX
import io.specmatic.core.EXAMPLES_DIR_SUFFIX
import io.specmatic.core.SPECMATIC_STUB_DICTIONARY
import io.specmatic.core.log.*
import picocli.CommandLine.Option
import java.io.File
import java.util.concurrent.Callable

abstract class ExamplesBase<Feature, Scenario>(protected open val featureStrategy: ExamplesFeatureStrategy<Feature, Scenario>) : Callable<Int> {
    @Option(names = ["--filter-name"], description = ["Use only APIs with this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NAME}")
    private var filterName: String = ""

    @Option(names = ["--filter-not-name"], description = ["Use only APIs which do not have this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}")
    private var filterNotName: String = ""

    @Option(names = ["--debug"], description = ["Debug logs"])
    private var verbose = false

    protected abstract var contractFile: File?
    protected abstract var extensive: Boolean

    override fun call(): Int {
        configureLogger(verbose)

        return contractFile?.let { contract ->
            getValidatedContractFileOrNull(contract)?.let {
                execute(it)
            } ?: 1
        } ?: execute(contractFile)
    }

    // HOOKS
    protected abstract fun execute(contract: File?): Int

    // HELPER METHODS
    private fun configureLogger(verbose: Boolean) {
        val logPrinters = listOf(ConsolePrinter)

        logger = if (verbose)
            Verbose(CompositePrinter(logPrinters))
        else
            NonVerbose(CompositePrinter(logPrinters))
    }

    protected fun getFilteredScenarios(feature: Feature): List<Scenario> {
        val scenarioFilter = ScenarioFilter(filterName, filterNotName)
        val scenarios = featureStrategy.getScenariosFromFeature(feature, extensive)
        return getFilteredScenarios(scenarios, scenarioFilter)
    }

    protected fun getValidatedContractFileOrNull(contractFile: File): File? {
        if (!contractFile.exists()) {
            consoleLog("Contract file does not exist: ${contractFile.absolutePath}")
            return null
        }

        if (contractFile.extension !in featureStrategy.contractFileExtensions) {
            consoleLog("Invalid Contract file ${contractFile.path} - File extension must be one of ${featureStrategy.contractFileExtensions.joinToString()}")
            return null
        }

        return contractFile
    }

    protected fun getValidatedExampleFileOrNull(exampleFile: File): File? {
        if (!exampleFile.exists()) {
            consoleLog("Example file does not exist: ${exampleFile.absolutePath}")
            return null
        }

        if (exampleFile.extension !in featureStrategy.exampleFileExtensions) {
            consoleLog("Invalid Example file ${exampleFile.path} - File extension must be one of ${featureStrategy.exampleFileExtensions.joinToString()}")
            return null
        }

        return exampleFile
    }

    protected fun getExamplesDirectory(contractFile: File): File {
        val examplesDirectory = contractFile.canonicalFile.parentFile.resolve("${contractFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX")
        if (!examplesDirectory.exists()) {
            consoleLog("Creating examples directory: $examplesDirectory")
            examplesDirectory.mkdirs()
        }
        return examplesDirectory
    }

    protected fun getExternalExampleFiles(examplesDirectory: File): List<File> {
        return examplesDirectory.walk().filter {
            it.isFile && it.extension in featureStrategy.exampleFileExtensions
        }.toList()
    }

    protected fun getExternalExampleFilesFromContract(contractFile: File): List<File> {
        return getExternalExampleFiles(getExamplesDirectory(contractFile))
    }

    protected fun logSeparator(length: Int, separator: String = "-") {
        consoleLog(separator.repeat(length))
    }

    protected fun logFormattedOutput(header: String, summary: String, note: String) {
        val maxLength = maxOf(summary.length, note.length, 50).let { it + it % 2 }
        val headerSidePadding = (maxLength - 2 - header.length) / 2

        val paddedHeaderLine = "=".repeat(headerSidePadding) + " $header " + "=".repeat(headerSidePadding)
        val paddedSummaryLine = summary.padEnd(maxLength)
        val paddedNoteLine = note.padEnd(maxLength)

        consoleLog("\n$paddedHeaderLine")
        consoleLog(paddedSummaryLine)
        consoleLog("=".repeat(maxLength))
        consoleLog(paddedNoteLine)
    }

    protected fun updateDictionaryFile(dictFileFromArgs: File? = null, contract: File? = contractFile) {
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
            System.setProperty(SPECMATIC_STUB_DICTIONARY, it.absolutePath)
        }
    }

    private fun getFilteredScenarios(scenarios: List<Scenario>, scenarioFilter: ScenarioFilter): List<Scenario> {
        val filteredScenarios = scenarios
            .filterScenarios(scenarioFilter.filterNameTokens, shouldMatch = true)
            .filterScenarios(scenarioFilter.filterNotNameTokens, shouldMatch = false)

        if (filteredScenarios.isEmpty()) {
            consoleLog("Note: All examples were filtered out by the filter expression")
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
