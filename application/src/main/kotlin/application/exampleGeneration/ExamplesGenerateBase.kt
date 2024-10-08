package application.exampleGeneration

import io.specmatic.core.*
import io.specmatic.core.log.*
import picocli.CommandLine.*
import java.io.File

abstract class ExamplesGenerateBase<Feature, Scenario>(
    override val featureStrategy: ExamplesFeatureStrategy<Feature, Scenario>,
    private val generationStrategy: ExamplesGenerationStrategy<Feature, Scenario>
): ExamplesBase<Feature, Scenario>(featureStrategy) {
    @Parameters(index = "0", description = ["Contract file path"], arity = "0..1")
    public override var contractFile: File? = null

    @Option(names = ["--dictionary"], description = ["Path to external dictionary file (default: contract_file_name_dictionary.json or dictionary.json)"])
    private var dictFile: File? = null

    abstract var extensive: Boolean

    override fun execute(contract: File?): Int {
        if (contract == null) {
            logger.log("No contract file provided. Use a subcommand or provide a contract file. Use --help for more details.")
            return 1
        }

        try {
            updateDictionaryFile(dictFile)
            val examplesDir = getExamplesDirectory(contract)
            val result = generateExamples(contract, examplesDir)
            logGenerationResult(result, examplesDir)
            return 0
        } catch (e: Throwable) {
            logger.log("Example generation failed with error: ${e.message}")
            logger.debug(e)
            return 1
        }
    }

    // GENERATOR METHODS
    private fun generateExamples(contractFile: File, examplesDir: File): List<ExampleGenerationResult> {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val filteredScenarios = getFilteredScenarios(feature, extensive)

        if (filteredScenarios.isEmpty()) {
            return emptyList()
        }

        val exampleFiles = getExternalExampleFiles(examplesDir)
        return filteredScenarios.map { scenario ->
            generationStrategy.generateOrGetExistingExample(
                ExamplesGenerationStrategy.GenerateOrGetExistingExampleArgs(
                    feature, scenario,
                    featureStrategy.getScenarioDescription(scenario),
                    exampleFiles, examplesDir,
                    featureStrategy.exampleFileExtensions
                )
            ).also { logSeparator(75) }
        }
    }

    // HELPER METHODS
    private fun logGenerationResult(generations: List<ExampleGenerationResult>, examplesDir: File) {
        val generationGroup = generations.groupBy { it.status }.mapValues { it.value.size }
        val createdFileCount = generationGroup[ExampleGenerationStatus.CREATED] ?: 0
        val errorCount = generationGroup[ExampleGenerationStatus.ERROR] ?: 0
        val existingCount = generationGroup[ExampleGenerationStatus.EXISTS] ?: 0
        val examplesDirectory = examplesDir.canonicalFile.absolutePath

        logFormattedOutput(
            header = "Example Generation Summary",
            summary = "$createdFileCount example(s) created, $existingCount example(s) already existed, $errorCount example(s) failed",
            note = "NOTE: All examples can be found in $examplesDirectory"
        )
    }
}

enum class ExampleGenerationStatus(val value: String) {
    CREATED("Inline"),
    ERROR("External"),
    EXISTS("Exists");

    override fun toString(): String {
        return this.value
    }
}

data class ExampleGenerationResult(val exampleFile: File? = null, val status: ExampleGenerationStatus)

interface ExamplesGenerationStrategy<Feature, Scenario> {
    fun getExistingExampleOrNull(scenario: Scenario, exampleFiles: List<File>): Pair<File, Result>?

    fun generateExample(feature: Feature, scenario: Scenario): Pair<String, String>

    fun generateOrGetExistingExample(request: GenerateOrGetExistingExampleArgs<Feature, Scenario>): ExampleGenerationResult {
        return try {
            val existingExample = getExistingExampleOrNull(request.scenario, request.exampleFiles)
            val scenarioDescription = request.scenarioDescription

            if (existingExample != null) {
                logger.log("Using existing example for ${scenarioDescription}\nExample File: ${existingExample.first.absolutePath}")
                return ExampleGenerationResult(existingExample.first, ExampleGenerationStatus.EXISTS)
            }

            logger.log("Generating example for $scenarioDescription")
            val (uniqueFileName, exampleContent) = generateExample(request.feature, request.scenario)
            return writeExampleToFile(exampleContent, uniqueFileName, request.examplesDir, request.validExampleExtensions)
        } catch (e: Throwable) {
            logger.log("Failed to generate example: ${e.message}")
            logger.debug(e)
            ExampleGenerationResult(null, ExampleGenerationStatus.ERROR)
        }
    }

    fun writeExampleToFile(exampleContent: String, exampleFileName: String, examplesDir: File, validExampleExtensions: Set<String>): ExampleGenerationResult {
        val exampleFile = examplesDir.resolve(exampleFileName)

        if (exampleFile.extension !in validExampleExtensions) {
            logger.log("Invalid example file extension: ${exampleFile.extension}")
            return ExampleGenerationResult(exampleFile, ExampleGenerationStatus.ERROR)
        }

        try {
            exampleFile.writeText(exampleContent)
            logger.log("Successfully saved example: $exampleFile")
            return ExampleGenerationResult(exampleFile, ExampleGenerationStatus.CREATED)
        } catch (e: Throwable) {
            logger.log("Failed to save example: $exampleFile")
            logger.debug(e)
            return ExampleGenerationResult(exampleFile, ExampleGenerationStatus.ERROR)
        }
    }

    data class GenerateOrGetExistingExampleArgs<Feature, Scenario> (
        val feature: Feature,
        val scenario: Scenario,
        val scenarioDescription: String,
        val exampleFiles: List<File>,
        val examplesDir: File,
        val validExampleExtensions: Set<String>
    )
}
