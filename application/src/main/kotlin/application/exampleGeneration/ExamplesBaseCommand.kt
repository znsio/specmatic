package application.exampleGeneration

import io.specmatic.core.EXAMPLES_DIR_SUFFIX
import io.specmatic.core.log.*
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

abstract class ExamplesBaseCommand<Feature, Scenario>: Callable<Int> {

    @Parameters(index = "0", description = ["Contract file path"], arity = "0..1")
    lateinit var contractFile: File

    @Option(names = ["--debug"], description = ["Debug logs"])
    var verbose = false

    override fun call(): Int {
        configureLogger(this.verbose)

        if (!contractFile.exists()) {
            logger.log("Could not find file ${contractFile.path}")
            return 1 // Failure
        }

        return try {
            val result = generateExamples()
            logGenerationResult(result)
            0 // Success
        } catch (e: Throwable) {
            logger.log("Example generation failed with error: ${e.message}")
            logger.debug(e)
            1 // Failure
        }
    }

    abstract fun contractFileToFeature(contractFile: File): Feature

    abstract fun getScenariosFromFeature(feature: Feature): List<Scenario>

    abstract fun getScenarioDescription(feature: Feature, scenario: Scenario): String

    abstract fun generateExampleFromScenario(feature: Feature, scenario: Scenario): Pair<String, String>

    private fun configureLogger(verbose: Boolean) {
        val logPrinters = listOf(ConsolePrinter)

        logger = if (verbose)
            Verbose(CompositePrinter(logPrinters))
        else
            NonVerbose(CompositePrinter(logPrinters))
    }

    private fun getExamplesDirectory(): File {
        val examplesDirectory = contractFile.canonicalFile.parentFile.resolve("${this.contractFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX")
        if (!examplesDirectory.exists()) {
            logger.log("Creating examples directory: $examplesDirectory")
            examplesDirectory.mkdirs()
        }
        return examplesDirectory
    }

    private fun generateExamples(): List<ExampleGenerationResult> {
        val feature = contractFileToFeature(contractFile)

        return getScenariosFromFeature(feature).map { scenario ->
            val description = getScenarioDescription(feature, scenario)

            try {
                logger.log("Generating example for $description")
                val (uniqueName, exampleContent) = generateExampleFromScenario(feature, scenario)
                writeExampleToFile(exampleContent, uniqueName)
            } catch (e: Throwable) {
                logger.log("Failed to generate example for $description")
                logger.debug(e)
                ExampleGenerationResult(status = ExampleGenerationStatus.ERROR)
            } finally {
                logger.log("----------------------------------------")
            }
        }
    }

    private fun writeExampleToFile(exampleContent: String, exampleFileName: String): ExampleGenerationResult {
        val exampleFile = getExamplesDirectory().resolve("$exampleFileName.json")

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

    private fun logGenerationResult(result: List<ExampleGenerationResult>) {
        val resultCounts = result.groupBy { it.status }.mapValues { it.value.size }
        val createdFileCount = resultCounts[ExampleGenerationStatus.CREATED] ?: 0
        val errorCount = resultCounts[ExampleGenerationStatus.ERROR] ?: 0
        val examplesDirectory = getExamplesDirectory().canonicalFile

        logger.log("\nNOTE: All examples can be found in $examplesDirectory")
        logger.log("=============== Example Generation Summary ===============")
        logger.log("$createdFileCount example(s) created, $errorCount examples(s) failed")
        logger.log("==========================================================")
    }

    enum class ExampleGenerationStatus {
        CREATED, ERROR
    }

    data class ExampleGenerationResult(val exampleFile: File? = null, val status: ExampleGenerationStatus)
}