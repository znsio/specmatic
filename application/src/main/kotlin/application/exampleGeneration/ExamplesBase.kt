package application.exampleGeneration

import io.specmatic.core.*
import io.specmatic.core.log.*
import picocli.CommandLine.*
import java.io.File
import java.util.concurrent.Callable

abstract class ExamplesBase<Feature, Scenario>(private val common: ExamplesCommon<Feature, Scenario>): Callable<Int> {
    @Parameters(index = "0", description = ["Contract file path"], arity = "0..1")
    private var contractFile: File? = null

    @Option(names = ["--filter-name"], description = ["Use only APIs with this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NAME}")
    var filterName: String = ""

    @Option(names = ["--filter-not-name"], description = ["Use only APIs which do not have this value in their name, Case sensitive"], defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}")
    var filterNotName: String = ""

    @Option(names = ["--dictionary"], description = ["External Dictionary File Path, defaults to dictionary.json"])
    private var dictFile: File? = null

    @Option(names = ["--debug"], description = ["Debug logs"])
    private var verbose = false

    abstract var extensive: Boolean

    override fun call(): Int {
        common.configureLogger(this.verbose)

        contractFile?.let { contract ->
            if (!contract.exists()) {
                logger.log("Could not find Contract file ${contract.path}")
                return 1
            }

            if (contract.extension !in common.contractFileExtensions) {
                logger.log("Invalid Contract file ${contract.path} - File extension must be one of ${common.contractFileExtensions.joinToString()}")
                return 1
            }

            try {
                val externalDictionary = common.loadExternalDictionary(dictFile, contract)
                val examplesDir = common.getExamplesDirectory(contract)
                val result = generateExamples(contract, externalDictionary, examplesDir)
                logGenerationResult(result, examplesDir)
                return 0
            } catch (e: Throwable) {
                logger.log("Example generation failed with error: ${e.message}")
                logger.debug(e)
                return 1
            }
        } ?: run {
            logger.log("No contract file provided. Use a subcommand or provide a contract file. Use --help for more details.")
            return 1
        }
    }

    // GENERATION METHODS
    private fun getFilteredScenarios(feature: Feature): List<Scenario> {
        val scenarioFilter = ScenarioFilter(filterName, filterNotName, extensive)
        return common.getFilteredScenarios(feature, scenarioFilter)
    }

    private fun generateExamples(contractFile: File, externalDictionary: Dictionary, examplesDir: File): List<ExampleGenerationResult> {
        val feature = common.contractFileToFeature(contractFile)
        val filteredScenarios = getFilteredScenarios(feature)

        if (filteredScenarios.isEmpty()) {
            return emptyList()
        }

        val exampleFiles = common.getExternalExampleFiles(examplesDir)
        return filteredScenarios.map { scenario ->
            common.generateOrGetExistingExample(feature, scenario, externalDictionary, exampleFiles, examplesDir)
        }
    }

    // HELPERS
    private fun logGenerationResult(generations: List<ExampleGenerationResult>, examplesDir: File) {
        val generationGroup = generations.groupBy { it.status }.mapValues { it.value.size }
        val createdFileCount = generationGroup[ExampleGenerationStatus.CREATED] ?: 0
        val errorCount = generationGroup[ExampleGenerationStatus.ERROR] ?: 0
        val existingCount = generationGroup[ExampleGenerationStatus.EXISTS] ?: 0
        val examplesDirectory = examplesDir.canonicalFile.absolutePath

        common.logFormattedOutput(
            header = "Example Generation Summary",
            summary = "$createdFileCount example(s) created, $existingCount example(s) already existed, $errorCount example(s) failed",
            note = "NOTE: All examples can be found in $examplesDirectory"
        )
    }
}

enum class ExampleGenerationStatus {
    CREATED, ERROR, EXISTS
}

data class ExampleGenerationResult(val exampleFile: File? = null, val status: ExampleGenerationStatus)