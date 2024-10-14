package application.exampleGeneration

import io.ktor.server.application.*
import io.specmatic.core.Result
import io.specmatic.core.log.consoleDebug
import io.specmatic.core.log.consoleLog
import io.specmatic.examples.*
import picocli.CommandLine.Option
import java.io.File
import java.util.concurrent.CountDownLatch

abstract class ExamplesInteractiveBase<Feature, Scenario> (
    override val featureStrategy: ExamplesFeatureStrategy<Feature, Scenario>,
    private val generationStrategy: ExamplesGenerationStrategy<Feature, Scenario>,
    private val validationStrategy: ExamplesValidationStrategy<Feature, Scenario>
): ExamplesBase<Feature, Scenario>(featureStrategy), InteractiveServerProvider {
    @Option(names = ["--testBaseURL"], description = ["BaseURL of the the system to test"], required = false)
    override var sutBaseUrl: String? = null

    @Option(names = ["--contract-file"], description = ["Contract file path"], required = false)
    override var contractFile: File? = null

    @Option(names = ["--dictionary"], description = ["Path to external dictionary file (default: contract_file_name_dictionary.json or dictionary.json)"])
    protected var dictFile: File? = null

    override val serverHost: String = "0.0.0.0"
    override val serverPort: Int = 9001
    abstract val server: ExamplesInteractiveServer

    override fun execute(contract: File?): Int {
        try {
            if (contract == null) {
                consoleLog("Contract file not provided, Please provide one via HTTP request")
            }

            updateDictionaryFile(dictFile)

            val latch = CountDownLatch(1)
            server.start()
            addShutdownHook(server, latch)
            consoleLog("Examples Interactive server is running on http://0.0.0.0:9001/_specmatic/examples. Ctrl + C to stop.")
            latch.await()
            return 0
        } catch (e: Throwable) {
            consoleLog("Example Interactive server failed with error: ${e.message}")
            consoleDebug(e)
            return 1
        }
    }

    // HOOKS
    abstract fun createTableRows(scenarioExamplePair: List<Pair<Scenario, ExampleValidationResult?>>): List<ExampleTableRow>

    abstract suspend fun getScenarioFromRequestOrNull(call: ApplicationCall, feature: Feature): Scenario?

    abstract fun testExternalExample(feature: Feature, exampleFile: File, testBaseUrl: String): Pair<Result, String>

    // HELPER METHODS
    override suspend fun generateExample(call: ApplicationCall, contractFile: File): ExampleGenerationResult {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val examplesDir = getExamplesDirectory(contractFile)
        val exampleFiles = getExternalExampleFiles(examplesDir)

        return getScenarioFromRequestOrNull(call, feature)?.let {
            generationStrategy.generateOrGetExistingExample(
                ExamplesGenerationStrategy.GenerateOrGetExistingExampleArgs(
                    feature, it,
                    featureStrategy.getScenarioDescription(it),
                    exampleFiles, examplesDir,
                    featureStrategy.exampleFileExtensions
                )
            )
        } ?: throw IllegalArgumentException("No matching scenario found for request")
    }

    override fun validateExample(contractFile: File, exampleFile: File): ExampleValidationResult {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val result = validationStrategy.validateExternalExample(feature, exampleFile)
        return ExampleValidationResult(exampleFile.absolutePath, result.second, ExampleType.EXTERNAL).also {
            it.logErrors()
        }
    }

    override fun testExample(contractFile: File, exampleFile: File, sutBaseUrl: String): ExampleTestResult {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val result = testExternalExample(feature, exampleFile, sutBaseUrl)
        return ExampleTestResult(result.first, result.second, exampleFile)
    }

    override fun getTableRows(contractFile: File): List<ExampleTableRow> {
        val feature = featureStrategy.contractFileToFeature(contractFile)
        val scenarios = getFilteredScenarios(feature)
        val examplesDir = getExamplesDirectory(contractFile)
        val examples = getExternalExampleFiles(examplesDir)

        val scenarioExamplePair = scenarios.map {
            it to generationStrategy.getExistingExampleOrNull(it, examples)?.let { exRes ->
                ExampleValidationResult(exRes.first, exRes.second)
            }
        }
        return createTableRows(scenarioExamplePair)
    }

    private fun addShutdownHook(server: ExamplesInteractiveServer, latch: CountDownLatch) {
        Runtime.getRuntime().addShutdownHook(Thread {
            consoleLog("Shutdown signal received (Ctrl + C).")
            latch.countDown()
            try {
                server.close()
                consoleLog("Server shutdown completed successfully.")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                consoleLog("Server shutdown interrupted.")
            } catch (e: Throwable) {
                consoleLog("Server shutdown failed with error: ${e.message}")
                consoleDebug(e)
            }
        })
    }

    data class ValueWithInfo<T>(
        val value: T,
        val rawValue: String,
        val extraInfo: String?
    )
}
