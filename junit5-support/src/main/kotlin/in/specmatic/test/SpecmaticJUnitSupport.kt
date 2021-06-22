package `in`.specmatic.test

import `in`.specmatic.core.*
import `in`.specmatic.core.Configuration.Companion.DEFAULT_CONFIG_FILE_NAME
import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.Examples
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.opentest4j.TestAbortedException
import java.io.File

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

        val config = loadConfigJSON(File(globalConfigFileName))
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

        val workingDirectory = File(valueOrDefault(givenWorkingDirectory, DEFAULT_WORKING_DIRECTORY, "Working was not specified specified"))
        val workingDirectoryWasCreated = workingDirectory.exists()

        val envConfig = getEnvConfig(System.getProperty(ENV_NAME))
        val testConfig = loadTestConfig(envConfig)

        val testScenarios = try {
            when {
                contractPaths != null -> {
                    contractPaths.split(",").flatMap { loadTestScenarios(it, suggestionsPath, suggestionsData, testConfig) }
                }
                else -> {
                    val configFile = valueOrDefault(givenConfigFile, DEFAULT_CONFIG_FILE_NAME, "Neither contract nor config were specified")

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
                val result: Result = testScenario.runTest(System.getProperty(HOST), System.getProperty(PORT), timeout)

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
        return givenConfigFilePath ?: default.also { println("$reason, defaulting to $it") }
    }

    private fun loadTestScenarios(
        path: String,
        suggestionsPath: String,
        suggestionsData: String,
        config: TestConfig
    ): List<ContractTest> {
        val feature = parseGherkinStringToFeature(readFile(path), File(path).absolutePath).copy(testVariables = config.variables, testBaseURLs = config.baseURLs)

        val suggestions = when {
            suggestionsPath.isNotEmpty() -> suggestionsFromFile(suggestionsPath)
            suggestionsData.isNotEmpty() -> suggestionsFromCommandLine(suggestionsData)
            else -> emptyList()
        }

        return feature.generateContractTests(suggestions)
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
