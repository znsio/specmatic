package application

import io.specmatic.core.Configuration
import io.specmatic.core.Configuration.Companion.DEFAULT_HTTP_STUB_HOST
import io.specmatic.core.Configuration.Companion.DEFAULT_HTTP_STUB_PORT
import io.specmatic.core.DEFAULT_WORKING_DIRECTORY
import io.specmatic.core.Feature
import io.specmatic.core.Scenario
import io.specmatic.core.log.StringLog
import io.specmatic.core.log.consoleLog
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.utilities.*
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.stateful.StatefulHttpStub
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch

@Command(
    name = "virtual-service",
    mixinStandardHelpOptions = true,
    description = ["Start a stateful virtual service with contract"]
)
class VirtualServiceCommand : Callable<Int> {

    @Option(names = ["--host"], description = ["Host for the virtual service"], defaultValue = DEFAULT_HTTP_STUB_HOST)
    lateinit var host: String

    @Option(names = ["--port"], description = ["Port for the virtual service"], defaultValue = DEFAULT_HTTP_STUB_PORT)
    var port: Int = 0

    @Option(names = ["--examples"], description = ["Directories containing JSON examples"], required = false)
    var exampleDirs: List<String> = mutableListOf()

    private val stubLoaderEngine = StubLoaderEngine()
    private var server: StatefulHttpStub? = null
    private val latch = CountDownLatch(1)
    private val newLine = System.lineSeparator()

    override fun call(): Int {
        setup()

        try {
            startServer()
        } catch (e: Exception) {
            logger.log("An error occurred while starting the virtual service: ${e.message}")
            return 1
        }

        return 0
    }

    private fun setup() {
        exitIfContractPathsDoNotExist()
        addShutdownHook()
    }

    private fun exitIfContractPathsDoNotExist() {
        val contractPaths = contractStubPaths(Configuration.configFilePath).map { it.path }
        exitIfAnyDoNotExist("The following specifications do not exist", contractPaths)
    }

    private fun addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                try {
                    latch.countDown()
                    consoleLog(StringLog("Shutting down the virtual service"))
                    server?.close()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        })
    }

    private fun stubContractPathData(): List<ContractPathData> {
        return contractFilePathsFrom(Configuration.configFilePath, DEFAULT_WORKING_DIRECTORY) { source ->
            source.stubContracts
        }
    }

    private fun startServer() {
        val stubData: List<Pair<Feature, List<ScenarioStub>>> = stubLoaderEngine.loadStubs(
            stubContractPathData(),
            exampleDirs,
            Configuration.configFilePath,
            false
        )

        val validateSpec = virtualServiceValidationRuleset(stubData.map { it.first }.flatMap { it.scenarios })

        validateSpec.takeIf { it.isNotEmpty() }?.let {
            logger.log("\n\nThe following errors were found in the specifications:")
            logger.log(it.joinToString(System.lineSeparator()))
            return
        }

        server = StatefulHttpStub(
            host,
            port,
            stubData.map { it.first },
            Configuration.configFilePath,
            stubData.flatMap { it.second }.also { it.logExamplesCachedAsSeedData() }
        )
        logger.log("Virtual service is running on ${consolePrintableURL(host, port)}. Ctrl + C to stop.")
        latch.await()
    }

    private fun List<ScenarioStub>.logExamplesCachedAsSeedData() {
        logger.log("${newLine}Injecting the data read from the following stub files into the stub server's state..".prependIndent("  "))
        this.forEach { logger.log(it.filePath.orEmpty().prependIndent("  ")) }
    }

    companion object {
        fun virtualServiceValidationRuleset(scenarios: List<Scenario>): List<String> {
            return scenarios.flatMap { scenario ->
                ALL_VALIDATORS.mapNotNull { it.validate(scenario) }
            }
        }

        interface ScenarioValidator {
            fun validate(scenario: Scenario): String?
        }

        private val ALL_VALIDATORS = listOf(HttpMethodValidator(), PostResponseValidator())

        class HttpMethodValidator : ScenarioValidator {
            private val supportedMethods = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
            override fun validate(scenario: Scenario): String? {
                if (scenario.method !in supportedMethods) {
                    val supportedMethods = supportedMethods.joinToString(", ")
                    return "Invalid HTTP method ${scenario.method} in path ${scenario.path}. Supported methods are: $supportedMethods"
                }
                return null
            }
        }

        class PostResponseValidator : ScenarioValidator {
            override fun validate(scenario: Scenario): String? {
                if (scenario.method == "POST" && scenario.isA2xxScenario()) {
                    val responsePattern = scenario.resolvedResponseBodyPattern()
                    if (responsePattern is JSONObjectPattern && !responsePattern.pattern.keys.any { it in setOf("id", "id?") }) {
                        return "Operation: ${scenario.apiDescription}, must contain 'id' key in the response for POST requests"
                    }
                }
                return null
            }
        }

        class ValidateResourcePathParams : ScenarioValidator {
            override fun validate(scenario: Scenario): String? {
                val pathSegments = scenario.path.split("/").filter(String::isNotEmpty)
                if (pathSegments.size > 1) {
                    val potentialId = pathSegments[1]
                    if (!potentialId.startsWith("(") && !potentialId.endsWith(")")) {
                        return "Operation ${scenario.apiDescription}, contains invalid nested resource '$potentialId'. Use flat structure: /resource or /resource/{id}"
                    }
                }
                return null
            }
        }
    }
}
