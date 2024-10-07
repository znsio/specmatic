package application.exampleGeneration

import io.specmatic.core.*
import io.specmatic.core.log.*
import io.specmatic.mock.loadDictionary
import picocli.CommandLine.*
import java.io.File

abstract class ExamplesGenerateBase<Feature, Scenario>: ExamplesBase<Feature, Scenario>(), ExamplesGenerateCommon<Feature, Scenario> {
    @Parameters(index = "0", description = ["Contract file path"], arity = "0..1")
    override var contractFile: File? = null

    @Option(names = ["--dictionary"], description = ["External Dictionary File Path, defaults to dictionary.json"])
    private var dictFile: File? = null

    abstract var extensive: Boolean

    override fun execute(contract: File?): Int {
        if (contract == null) {
            logger.log("No contract file provided. Use a subcommand or provide a contract file. Use --help for more details.")
            return 1
        }

        try {
            val externalDictionary = loadExternalDictionary(dictFile, contract)
            val examplesDir = getExamplesDirectory(contract)
            val result = generateExamples(contract, externalDictionary, examplesDir)
            logGenerationResult(result, examplesDir)
            return 0
        } catch (e: Throwable) {
            logger.log("Example generation failed with error: ${e.message}")
            logger.debug(e)
            return 1
        }
    }

    // GENERATOR METHODS
    private fun generateExamples(contractFile: File, externalDictionary: Dictionary, examplesDir: File): List<ExampleGenerationResult> {
        val feature = contractFileToFeature(contractFile)
        val filteredScenarios = getFilteredScenarios(feature, extensive)

        if (filteredScenarios.isEmpty()) {
            return emptyList()
        }

        val exampleFiles = getExternalExampleFiles(examplesDir)
        return filteredScenarios.map { scenario ->
            generateOrGetExistingExample(feature, scenario, externalDictionary, exampleFiles, examplesDir).also {
                logSeparator(75)
            }
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

interface ExamplesGenerateCommon<Feature, Scenario> : ExamplesCommon<Feature, Scenario> {
    fun getExistingExampleOrNull(scenario: Scenario, exampleFiles: List<File>): Pair<File, Result>?

    fun generateExample(feature: Feature, scenario: Scenario, dictionary: Dictionary): Pair<String, String>

    fun loadExternalDictionary(dictFile: File?, contractFile: File?): Dictionary {
        val dictFilePath = when(dictFile != null) {
            true -> {
                dictFile.takeIf { it.exists() }?.path ?: throw IllegalStateException("Dictionary file not found: ${dictFile.path}")
            }

            false -> {
                val contractDictFile = contractFile?.let { contract ->
                    val contractDictFile = "${contract.nameWithoutExtension}$DICTIONARY_FILE_SUFFIX"
                    contract.canonicalFile.parentFile.resolve(contractDictFile).takeIf { it.exists() }?.path
                }

                val currentDirDictFile = File(System.getProperty("user.dir")).resolve("dictionary.json").takeIf {
                    it.exists()
                }?.path

                contractDictFile ?: currentDirDictFile
            }
        }

        return dictFilePath?.let {
            Dictionary(loadDictionary(dictFilePath))
        } ?: Dictionary(emptyMap())
    }

    fun generateOrGetExistingExample(feature: Feature, scenario: Scenario, externalDictionary: Dictionary, exampleFiles: List<File>, examplesDir: File): ExampleGenerationResult {
        return try {
            val existingExample = getExistingExampleOrNull(scenario, exampleFiles)
            val description = getScenarioDescription(scenario)

            if (existingExample != null) {
                logger.log("Using existing example for $description\nExample File: ${existingExample.first.absolutePath}")
                return ExampleGenerationResult(existingExample.first, ExampleGenerationStatus.EXISTS)
            }

            logger.log("Generating example for $description")
            val (uniqueFileName, exampleContent) = generateExample(feature, scenario, externalDictionary)
            return writeExampleToFile(exampleContent, uniqueFileName, examplesDir)
        } catch (e: Throwable) {
            logger.log("Failed to generate example: ${e.message}")
            logger.debug(e)
            ExampleGenerationResult(null, ExampleGenerationStatus.ERROR)
        }
    }

    fun writeExampleToFile(exampleContent: String, exampleFileName: String, examplesDir: File): ExampleGenerationResult {
        val exampleFile = examplesDir.resolve(exampleFileName)

        if (exampleFile.extension !in exampleFileExtensions) {
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
}
