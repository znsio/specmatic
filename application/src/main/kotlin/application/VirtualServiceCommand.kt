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
import io.specmatic.core.pattern.DeferredPattern
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
class VirtualServiceCommand  : Callable<Int> {

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
        } catch(e: Exception) {
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
        return contractFilePathsFrom(Configuration.configFilePath, DEFAULT_WORKING_DIRECTORY) {
                source -> source.stubContracts
        }
    }

    private fun startServer() {
        val stubData: List<Pair<Feature, List<ScenarioStub>>> = stubLoaderEngine.loadStubs(
            stubContractPathData(),
            exampleDirs,
            Configuration.configFilePath,
            false
        )

        val validateSpec = virtualServiceValidationRuleset(stubData.map{it.first}.flatMap{it.scenarios})

        if (validateSpec.isNotEmpty()) {
            logger.log("\n\nThe following errors were found in the specifications:")
            validateSpec.forEach { logger.log(it) }
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

    private fun virtualServiceValidationRuleset(scenario: List<Scenario>): MutableList<String> {
        val errors: MutableList<String> = mutableListOf()
        val supportedMethods = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
        scenario.forEach{ s ->
            // Check if the scenario is a valid HTTP method
            if(s.httpRequestPattern.method !in supportedMethods){
                errors.add("Invalid HTTP method ${s.httpRequestPattern.method} in path ${s.path}, The supported methods are ${supportedMethods.joinToString {", "}}")
            }

            // Check if the scenario is a POST with 2xx then it should contain an id in the response
            if (s.httpRequestPattern.method?.uppercase() == "POST" && s.isA2xxScenario()) {
                val responsePattern = when (val body = s.httpResponsePattern.body) {
                    is DeferredPattern -> s.patterns[body.pattern]
                    is JSONObjectPattern -> body
                    else -> null
                }

                if (responsePattern is JSONObjectPattern && !responsePattern.pattern.keys.contains("id")) {
                    errors.add("Operation: ${s.apiDescription}, does not contains <id> key in the response section as a required field")
                }
            }

            // Check if path is a valid REST path
            if (!isValidRestPath(s.path)) {
                errors.add("Invalid path: ${s.path}. Path should start with '/' and follow the REST conventions (e.g., /resources/{id})")
            }

            // Check if path contains any invalid resource names
            val pathSegments = s.path.split("/").filter { it.isNotEmpty() }
            pathSegments.forEach{ subPath ->
                if (!subPath.startsWith("{") && !subPath.endsWith("}") && !isValidResourceName(subPath)) {
                    errors.add("Invalid resource name in path ${s.path}: '$subPath' should be in lowercase with hyphens for separators")
                }
            }

            // Check if path contains nested resources
            if (pathSegments.size > 2) {
                val potentialId = pathSegments[1]
                if (!potentialId.startsWith("{") && !potentialId.endsWith("}")) {
                    errors.add("Invalid nested resource in path ${s.path}: Resources should follow flat structure like /resource or /resource/{id}")
                }
            }
        }
        return errors
    }

    private fun isValidRestPath(path: String): Boolean {
        return path.startsWith("/") &&
                !path.contains("//") &&
                !path.endsWith("/") &&
                path.all { it.isLetterOrDigit() || it == '/' || it == '-' || it == '{' || it == '}' || it == '_' }
    }

    private fun isValidResourceName(segment: String): Boolean {
        return segment.matches(Regex("^[a-z][a-z0-9-]*$"))
    }
}
