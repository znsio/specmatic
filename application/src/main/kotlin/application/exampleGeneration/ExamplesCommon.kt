package application.exampleGeneration

import io.specmatic.core.*
import io.specmatic.core.log.*
import io.specmatic.mock.loadDictionary
import java.io.File

interface ExamplesCommon<Feature, Scenario> {
    val exampleFileExtensions: Set<String>
    val contractFileExtensions: Set<String>

    fun contractFileToFeature(contractFile: File): Feature

    fun getScenariosFromFeature(feature: Feature, extensive: Boolean) : List<Scenario>

    fun getScenarioDescription(scenario: Scenario): String

    fun getExistingExampleOrNull(scenario: Scenario, exampleFiles: List<File>): Pair<File, Result>?

    fun generateExample(feature: Feature, scenario: Scenario, dictionary: Dictionary): Pair<String, String>

    fun updateFeatureForValidation(feature: Feature, filteredScenarios: List<Scenario>): Feature

    fun validateExternalExample(feature: Feature, exampleFile: File): Pair<String, Result>

    fun configureLogger(verbose: Boolean) {
        val logPrinters = listOf(ConsolePrinter)

        logger = if (verbose)
            Verbose(CompositePrinter(logPrinters))
        else
            NonVerbose(CompositePrinter(logPrinters))
    }

    fun getFilteredScenarios(feature: Feature, scenarioFilter: ScenarioFilter): List<Scenario> {
        val filteredScenarios = getScenariosFromFeature(feature, scenarioFilter.extensive)
            .filterScenarios(scenarioFilter.filterNameTokens, shouldMatch = true)
            .filterScenarios(scenarioFilter.filterNotNameTokens, shouldMatch = false)

        if (filteredScenarios.isEmpty()) {
            logger.log("Note: All examples were filtered out by the filter expression")
        }

        return filteredScenarios
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
        return examplesDirectory.walk().filter { it.isFile && it.extension in exampleFileExtensions }.toList()
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
        } finally {
            logSeparator(50)
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

    fun loadExternalDictionary(dictFile: File?, contractFile: File?): Dictionary {
        val dictFilePath = when {
            dictFile != null -> {
                if (!dictFile.exists()) throw IllegalStateException("Dictionary file not found: ${dictFile.path}")
                else dictFile.path
            }

            contractFile != null -> {
                val dictFileName = "${contractFile.nameWithoutExtension}$DICTIONARY_FILE_SUFFIX"
                contractFile.canonicalFile.parentFile.resolve(dictFileName).takeIf { it.exists() }?.path
            }

            else -> {
                val currentDir = File(System.getProperty("user.dir"))
                currentDir.resolve("dictionary.json").takeIf { it.exists() }?.path
            }
        }

        return dictFilePath?.let {
            Dictionary(loadDictionary(dictFilePath))
        } ?: Dictionary(emptyMap())
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

    private fun List<Scenario>.filterScenarios(tokens: Set<String>, shouldMatch: Boolean): List<Scenario> {
        if (tokens.isEmpty()) return this

        return this.filter {
            val description = getScenarioDescription(it)
            tokens.any { token ->
                description.contains(token)
            } == shouldMatch
        }
    }
}