package `in`.specmatic.test

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.opentest4j.TestAbortedException
import `in`.specmatic.core.*
import `in`.specmatic.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_FILE_NAME
import `in`.specmatic.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.testKafkaMessages
import java.io.File
import kotlin.system.exitProcess

open class SpecmaticJUnitSupport {
    companion object {
        const val CONTRACT_PATHS = "contractPaths"
        const val WORKING_DIRECTORY = "workingDirectory"
        const val CONFIG_FILE_NAME = "manifestFile"
        const val TIMEOUT = "timeout"
        private const val DEFAULT_TIMEOUT = "60"
        const val INLINE_SUGGESTIONS = "suggestions"
        const val SUGGESTIONS_PATH = "suggestionsPath"
        const val HOST = "host"
        const val PORT = "port"
        const val ENV_NAME = "environment"
    }

    private fun getEnvConfig(envName: String?): JSONObjectValue {
        if(envName == null || envName.isBlank())
            return JSONObjectValue()

        val config = loadConfigJSON(File(DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY))
        val envConfig = config.findFirstChildByPath("environments.$envName").also { println("First child: $it") } ?: return JSONObjectValue()

        if(envConfig !is JSONObjectValue)
            throw ContractException("The environment config must be a JSON object.")

        return envConfig
    }

    @TestFactory
    fun contractAsTest(): Collection<DynamicTest> {
        val contractPaths = System.getProperty(CONTRACT_PATHS)
        val givenWorkingDirectory = System.getProperty(WORKING_DIRECTORY)
        val givenConfigFile = System.getProperty(CONFIG_FILE_NAME)

        val timeout = System.getProperty(TIMEOUT, DEFAULT_TIMEOUT).toInt()

        val suggestionsData = System.getProperty(INLINE_SUGGESTIONS) ?: ""
        val suggestionsPath = System.getProperty(SUGGESTIONS_PATH) ?: ""

        val workingDirectory = File(valueOrDefault(givenWorkingDirectory, ".specmatic", "Working was not specified specified"))
        val workingDirectoryWasCreated = workingDirectory.exists()

        val envConfig = getEnvConfig(System.getProperty(ENV_NAME))
        val testConfig = loadTestConfig(envConfig)

        val testScenarios = try {
            when {
                contractPaths != null -> {
                    contractPaths.split(",").flatMap { loadTestScenarios(it, suggestionsPath, suggestionsData, testConfig) }
                }
                else -> {
                    val configFile = valueOrDefault(givenConfigFile, DEFAULT_QONTRACT_CONFIG_FILE_NAME, "Neither contract nor config were specified")

                    exitIfDoesNotExist("config file", configFile)

                    createIfDoesNotExist(workingDirectory.path)

                    val contractFilePaths = contractTestPathsFrom(configFile, workingDirectory.path).map { it.path }
                    contractFilePaths.flatMap { loadTestScenarios(it, "", "", testConfig) }
                }
            }
        } catch(e: ContractException) {
            println(e.report())
            throw e
        } catch(e: Throwable) {
            println(exceptionCauseMessage(e))
            throw e
        } finally {
            if(workingDirectoryWasCreated)
                workingDirectory.deleteRecursively()
        }

        return testScenarios.map { testScenario ->
            DynamicTest.dynamicTest(testScenario.testDescription()) {
                val kafkaMessagePattern = testScenario.kafkaMessagePattern

                val result = when {
                    kafkaMessagePattern != null -> runKafkaTest(testScenario)
                    else -> runHttpTest(timeout, testScenario)
                }.updateScenario(testScenario)

                when {
                    shouldBeIgnored(result) -> {
                        val message = "Test FAILED, ignoring since the scenario is tagged @WIP${System.lineSeparator()}${resultReport(result).prependIndent("  ")}"
                        throw TestAbortedException(message)
                    }
                    else -> ResultAssert.assertThat(result).isSuccess()
                }
            }
        }.toList()
    }

    private fun valueOrDefault(givenConfigFilePath: String?, default: String, reason: String): String {
        return when (givenConfigFilePath) {
            null -> default.also { println("$reason, defaulting to $it") }
            else -> givenConfigFilePath
        }
    }

    private fun runKafkaTest(testScenario: Scenario): Result {
        if (System.getProperty("kafkaPort") == null) {
            println("The contract has a kafka message. Please specify the port of the Kafka instance to connect to.")
            exitProcess(1)
        }

        val commit = "true" == System.getProperty("commit")

        return testKafkaMessages(testScenario, getBootstrapKafkaServers(), commit)
    }

    private fun runHttpTest(timeout: Int, testScenario: Scenario): Result {
        val host = System.getProperty(HOST)
        val port = System.getProperty(PORT)
        val protocol = System.getProperty("protocol") ?: "http"

        return executeTest(protocol, host, port, timeout, testScenario)
    }

    private fun executeTest(protocol: String, host: String?, port: String?, timeout: Int, testScenario: Scenario): Result {
        val httpClient = HttpClient("$protocol://$host:$port", timeout = timeout)
        return executeTest(testScenario, httpClient)
    }

    private fun loadTestScenarios(
        path: String,
        suggestionsPath: String,
        suggestionsData: String,
        config: TestConfig
    ): List<Scenario> {
        val feature = parseGherkinStringToFeature(readFile(path), File(path).absolutePath).copy(testVariables = config.variables, testBaseURLs = config.baseURLs)

        val suggestions = when {
            suggestionsPath.isNotEmpty() -> suggestionsFromFile(suggestionsPath)
            suggestionsData.isNotEmpty() -> suggestionsFromCommandLine(suggestionsData)
            else -> emptyList()
        }

        return feature.generateContractTestScenarios(suggestions)
    }

    private fun suggestionsFromFile(suggestionsPath: String): List<Scenario> {
        val suggestionsGherkin = readFile(suggestionsPath)
        return Suggestions(suggestionsGherkin).scenarios
    }

    private fun suggestionsFromCommandLine(suggestions: String): List<Scenario> {
        val suggestionsValue = parsedValue(suggestions)
        if (suggestionsValue !is JSONObjectValue)
            throw ContractException("Suggestions must be a json value with scenario name as the key, and json array with 1 or more json objects containing suggestions")

        return suggestionsValue.jsonObject.mapValues { (_, exampleData) ->
            when {
                exampleData !is JSONArrayValue -> throw ContractException("The value of a scenario must be a list of examples")
                exampleData.list.isEmpty() -> Examples()
                else -> {
                    val columns = columnsFromExamples(exampleData)

                    val rows = exampleData.list.map { row ->
                        asJSONObjectValue(row, "Each value in the list of suggestions must be a json object containing column name as key and sample value as the value")
                    }.map { row ->
                        Row(columns, columns.map { row.getValue(it).toStringValue() })
                    }.toMutableList()

                    Examples(columns, rows)
                }
            }
        }.entries.map { (name, examples) ->
            Scenario(
                name,
                HttpRequestPattern(),
                HttpResponsePattern(),
                emptyMap(),
                listOf(examples),
                emptyMap(),
                emptyMap(),
                null,
            )
        }
    }

    private fun getBootstrapKafkaServers(): String {
        return when {
            System.getProperty("kafkaBootstrapServers") != null && System.getProperty("kafkaBootstrapServers").isNotEmpty() ->
                System.getProperty("kafkaBootstrapServers")
            else -> {
                val kafkaPort = System.getProperty("kafkaPort")?.toInt() ?: 9093
                val kafkaHost = System.getProperty("kafkaHost") ?: "localhost"
                """PLAINTEXT://$kafkaHost:$kafkaPort"""
            }
        }
    }
}

private fun columnsFromExamples(exampleData: JSONArrayValue): List<String> {
    val firstRow = exampleData.list[0]
    if (firstRow !is JSONObjectValue)
        throw ContractException("Each value in the list of suggestions must be a json object containing column name as key and sample value as the value")

    return firstRow.jsonObject.keys.toList()
}

private fun asJSONObjectValue(value: Value, errorMessage: String): Map<String, Value> {
    if(value !is JSONObjectValue)
        throw ContractException(errorMessage)

    return value.jsonObject
}
