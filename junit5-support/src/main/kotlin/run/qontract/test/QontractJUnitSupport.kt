package run.qontract.test

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.opentest4j.TestAbortedException
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Examples
import run.qontract.core.pattern.parsedValue
import run.qontract.core.utilities.*
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.stub.testKafkaMessages
import java.io.File
import kotlin.system.exitProcess

val pass = Unit

open class QontractJUnitSupport {
    @TestFactory
    fun contractAsTest(): Collection<DynamicTest> {
        val path = System.getProperty("path")
        val givenWorkingDirectory = System.getProperty("workingDirectory")
        val givenManifestFile = System.getProperty("manifestFile")

        val timeout = System.getProperty("timeout", "60").toInt()

        val suggestionsData = System.getProperty("suggestions") ?: ""
        val suggestionsPath = System.getProperty("suggestionsPath") ?: ""
        val checkBackwardCompatibility = (System.getProperty("checkBackwardCompatibility") ?: "false").toBoolean()

        if(checkBackwardCompatibility) {
            checkBackwardCompatibilityInPath(path)
        }

        val testScenarios = try {
            when {
                path != null -> loadTestScenarios(path, suggestionsPath, suggestionsData)
                else -> {
                    val manifestFile = valueOrDefault(givenManifestFile, "qontract.json", "Neither contract nor manifest were specified")
                    val workingDirectory = valueOrDefault(givenWorkingDirectory, ".qontract", "Working was not specified specified")

                    exitIfDoesNotExist("manifest file", manifestFile)
                    createIfDoesNotExist(workingDirectory)

                    val contractFilePaths = contractFilePathsFrom(manifestFile, workingDirectory)
                    contractFilePaths.flatMap { loadTestScenarios(it, "", "") }
                }
            }
        } catch(e: ContractException) {
            println(e.report())
            throw e
        } catch(e: Throwable) {
            println(exceptionCauseMessage(e))
            throw e
        }

        return testScenarios.map { testScenario ->
            DynamicTest.dynamicTest(testScenario.toString()) {
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

    private fun valueOrDefault(givenManifestFile: String?, default: String, reason: String): String {
        return when (givenManifestFile) {
            null -> default.also { println("$reason, defaulting to $it") }
            else -> givenManifestFile
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
        val host = System.getProperty("host")
        val port = System.getProperty("port")
        val protocol = System.getProperty("protocol") ?: "http"

        return executeTest(protocol, host, port, timeout, testScenario)
    }

    private fun executeTest(protocol: String, host: String?, port: String?, timeout: Int, testScenario: Scenario): Result {
        val httpClient = HttpClient("$protocol://$host:$port", timeout = timeout)
        return executeTest(testScenario, httpClient)
    }

    private fun loadTestScenarios(path: String, suggestionsPath: String, suggestionsData: String): List<Scenario> {
        val feature = Feature(readFile(path))

        val suggestions = when {
            suggestionsPath.isNotEmpty() -> suggestionsFromFile(suggestionsPath)
            suggestionsData.isNotEmpty() -> suggestionsFromCommandLine(suggestionsData)
            else -> emptyList()
        }

        return feature.generateTestScenarios(suggestions)
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
            if (exampleData !is JSONArrayValue)
                throw ContractException("The value of a scenario must be a list of examples")

            if (exampleData.list.isEmpty())
                Examples()
            else {
                val firstRow = exampleData.list.get(0)
                if (firstRow !is JSONObjectValue)
                    throw ContractException("Each value in the list of suggestions must be a json object containing column name as key and sample value as the value")

                val columns = firstRow.jsonObject.keys.toList()

                Examples(columns.toMutableList()).apply {
                    for (row in exampleData.list) {
                        if (row !is JSONObjectValue)
                            throw ContractException("Each value in the list of suggestions must be a json object containing column name as key and sample value as the value")

                        val rowValues = columns.map { row.jsonObject.getValue(it).toStringValue() }
                        this.addRow(rowValues)
                    }
                }
            }
        }.entries.map { (name, examples) ->
            Scenario(name, HttpRequestPattern(), HttpResponsePattern(), emptyMap(), listOf(examples), emptyMap(), emptyMap(), null)
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

    private fun checkBackwardCompatibilityInPath(path: String) {
        val contractFile = File(path).absoluteFile
        val (majorVersion, minorVersion) = try {
            if (!path.endsWith(".$QONTRACT_EXTENSION"))
                throw ContractException("The path $path does not end with .qontract. Please make sure that the name is of the format <majorVesion>.<minorVersion if any>.qontract, for versioning to work properly.")

            val versionTokens = contractFile.nameWithoutExtension.split(".").map { it.toInt() }
            when (versionTokens.size) {
                1 -> Pair(versionTokens[0], 0)
                2 -> Pair(versionTokens[0], versionTokens[1])
                else -> throw ContractException("The name ($contractFile.name) does not seem to be a version number, so can't check for backward compatibility with prior versions.")
            }
        } catch (e: NumberFormatException) {
            throw ContractException("The name ($contractFile.name) does not seem to be a version number, so can't check for backward compatibility with prior versions.")
        }

        when (val result = testBackwardCompatibilityInDirectory(contractFile.parentFile, majorVersion, minorVersion)) {
            is TestResults ->
                if (result.list.any { !it.results.success() })
                    throw ContractException("Version incompatibility detected in the chain. Please verify that all contracts with this version are backward compatible.")
            is JustOne -> pass
            is NoContractsFound -> throw ContractException("Something is wrong, no contracts were found.")
        }
    }
}
