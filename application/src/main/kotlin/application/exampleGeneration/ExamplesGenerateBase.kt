package application.exampleGeneration

import io.specmatic.core.*
import io.specmatic.core.log.*
import io.specmatic.examples.ExampleGenerationResult
import io.specmatic.examples.ExampleGenerationStatus
import picocli.CommandLine.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

abstract class ExamplesGenerateBase<Feature, Scenario>(
    override val featureStrategy: ExamplesFeatureStrategy<Feature, Scenario>,
    private val generationStrategy: ExamplesGenerationStrategy<Feature, Scenario>
): ExamplesBase<Feature, Scenario>(featureStrategy) {
    @Parameters(paramLabel = "contractFile", description = ["Path to contract file"], arity = "0..1")
    public override var contractFile: File? = null

    @Option(names = ["--dictionary"], description = ["Path to external dictionary file (default: contract_file_name_dictionary.json or dictionary.json)"])
    private var dictFile: File? = null

    override fun execute(contract: File?): Int {
        if (contract == null) {
            consoleLog("No contract file provided. Use a subcommand or provide a contract file. Use --help for more details.")
            return 1
        }

        try {
            updateDictionaryFile(dictFile)
            val examplesDir = getExamplesDirectory(contract)
            val result = generateExamples(contract, examplesDir)
            return result.logGenerationResult(examplesDir).getExitCode()
        } catch (e: Throwable) {
            consoleLog("Example generation failed with error: ${e.message}")
            consoleDebug(e)
            return 1
        }
    }

    // GENERATOR METHODS
    private fun generateExamples(contractFile: File, examplesDir: File): List<ExampleGenerationResult> {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val filteredScenarios = getFilteredScenarios(feature)

        if (filteredScenarios.isEmpty()) {
            return emptyList()
        }

        val exampleFiles = getExternalExampleFiles(examplesDir)
        return filteredScenarios.flatMap { scenario ->
            generationStrategy.generateOrGetExistingExamples(
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
    private fun List<ExampleGenerationResult>.logGenerationResult(examplesDir: File): List<ExampleGenerationResult> {
        val generationGroup = this.groupBy { it.status }.mapValues { it.value.size }
        val createdFileCount = generationGroup[ExampleGenerationStatus.CREATED] ?: 0
        val errorCount = generationGroup[ExampleGenerationStatus.ERROR] ?: 0
        val existingCount = generationGroup[ExampleGenerationStatus.EXISTS] ?: 0
        val examplesDirectory = examplesDir.canonicalFile.absolutePath

        logFormattedOutput(
            header = "Example Generation Summary",
            summary = "$createdFileCount example(s) created, $existingCount example(s) already existed, $errorCount example(s) failed",
            note = "NOTE: All examples can be found in $examplesDirectory"
        )

        return this
    }

    private fun List<ExampleGenerationResult>.getExitCode(): Int {
        return if (this.any { it.status == ExampleGenerationStatus.ERROR }) 1 else 0
    }

    fun resetExampleFileNameCounter() {
        generationStrategy.exampleFileNamePostfixCounter.set(0)
    }
}

interface ExamplesGenerationStrategy<Feature, Scenario> {
    val exampleFileNamePostfixCounter: AtomicInteger

    fun getExistingExamples(scenario: Scenario, exampleFiles: List<File>): List<Pair<File, Result>>

    fun generateExample(feature: Feature, scenario: Scenario): Pair<String, String>

    fun generateOrGetExistingExamples(request: GenerateOrGetExistingExampleArgs<Feature, Scenario>): List<ExampleGenerationResult> {
        return try {
            val existingExamples = getExistingExamples(request.scenario, request.exampleFiles)
            val scenarioDescription = request.scenarioDescription

            if (existingExamples.isNotEmpty()) {
                consoleLog("Using existing example(s) for $scenarioDescription")
                existingExamples.forEach { consoleLog("Example File: ${it.first.absolutePath}\"") }
                return existingExamples.map { ExampleGenerationResult(it.first, ExampleGenerationStatus.EXISTS) }
            }

            consoleLog("Generating example for $scenarioDescription")
            val (uniqueFileName, exampleContent) = generateExample(request.feature, request.scenario)
            return listOf(writeExampleToFile(exampleContent, uniqueFileName, request.examplesDir, request.validExampleExtensions))
        } catch (e: Throwable) {
            consoleLog("Failed to generate example: ${e.message}")
            consoleDebug(e)
            listOf(ExampleGenerationResult(null, ExampleGenerationStatus.ERROR))
        }
    }

    fun writeExampleToFile(exampleContent: String, exampleFileName: String, examplesDir: File, validExampleExtensions: Set<String>): ExampleGenerationResult {
        val exampleFile = examplesDir.resolve(exampleFileName)

        if (exampleFile.extension !in validExampleExtensions) {
            consoleLog("Invalid example file extension: ${exampleFile.extension}")
            return ExampleGenerationResult(exampleFile, ExampleGenerationStatus.ERROR)
        }

        try {
            exampleFile.writeText(exampleContent)
            consoleLog("Successfully saved example: $exampleFile")
            return ExampleGenerationResult(exampleFile, ExampleGenerationStatus.CREATED)
        } catch (e: Throwable) {
            consoleLog("Failed to save example: $exampleFile")
            consoleDebug(e)
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
