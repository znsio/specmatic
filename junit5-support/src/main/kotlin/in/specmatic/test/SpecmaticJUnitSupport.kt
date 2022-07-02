package `in`.specmatic.test

import `in`.specmatic.core.*
import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.log.logger
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
        const val TEST_BASE_URL = "testBaseURL"
        const val ENV_NAME = "environment"
        const val VARIABLES_FILE_NAME = "variablesFileName"
        const val FILTER_NAME = "filterName"

        val testsNames = mutableListOf<String>()
    }

    private fun getEnvConfig(envName: String?): JSONObjectValue {
        if(envName == null || envName.isBlank())
            return JSONObjectValue()

        val config = loadConfigJSON(File(globalConfigFileName))
        val envConfig = config.findFirstChildByPath("environments.$envName") ?: return JSONObjectValue()

        if(envConfig !is JSONObjectValue)
            throw ContractException("The environment config must be a JSON object.")

        return envConfig
    }

    private fun loadExceptionAsTestError(e: Throwable): Collection<DynamicTest> {
        return listOf(DynamicTest.dynamicTest("Load Error") {
            testsNames.add("Load Error")
            logger.log(e)
            ResultAssert.assertThat(Result.Failure(exceptionCauseMessage(e))).isSuccess()
        })
    }

    @TestFactory
    fun contractTests(): Collection<DynamicTest> {
        val contractPaths = System.getProperty(CONTRACT_PATHS)
        val givenWorkingDirectory = System.getProperty(WORKING_DIRECTORY)
        val givenConfigFile = System.getProperty(CONFIG_FILE_NAME)
        val filterName: String? = System.getProperty(FILTER_NAME)

        val timeout = System.getProperty(TIMEOUT, DEFAULT_TIMEOUT).toInt()

        val suggestionsData = System.getProperty(INLINE_SUGGESTIONS) ?: ""
        val suggestionsPath = System.getProperty(SUGGESTIONS_PATH) ?: ""

        val workingDirectory = WorkingDirectory(givenWorkingDirectory ?: DEFAULT_WORKING_DIRECTORY)

        val envConfig = getEnvConfig(System.getProperty(ENV_NAME))
        val testConfig = try {
            loadTestConfig(envConfig).withVariablesFromFilePath(System.getProperty(VARIABLES_FILE_NAME))
        } catch (e: Throwable) {
            return loadExceptionAsTestError(e)
        }

        val testScenarios = try {
            val testScenarios = when {
                contractPaths != null -> {
                    contractPaths.split(",").flatMap { loadTestScenarios(it, suggestionsPath, suggestionsData, testConfig) }
                }
                else -> {
                    val configFile = givenConfigFile ?: globalConfigFileName

                    exitIfDoesNotExist("config file", configFile)

                    createIfDoesNotExist(workingDirectory.path)

                    val contractFilePaths = contractTestPathsFrom(configFile, workingDirectory.path).map { it.path }
                    contractFilePaths.flatMap { loadTestScenarios(it, "", "", testConfig) }
                }
            }

            if(filterName != null) {
                testScenarios.filter {
                    it.testDescription().contains(filterName)
                }
            } else
                testScenarios
        } catch(e: ContractException) {
            return loadExceptionAsTestError(e)
        } catch(e: Throwable) {
            return loadExceptionAsTestError(e)
        } finally {
            workingDirectory.delete()
        }

        val invoker = when(val testBaseURL = System.getProperty(TEST_BASE_URL)) {
            null -> TargetHostAndPort(System.getProperty(HOST), System.getProperty(PORT))
            else -> TargetBaseURL(testBaseURL)
        }

        return testScenarios.map { testScenario ->
            DynamicTest.dynamicTest(testScenario.testDescription()) {
                testsNames.add(testScenario.testDescription())

                val result: Result = invoker.execute(testScenario, timeout)

                when {
                    result.shouldBeIgnored() -> {
                        val message = "Test FAILED, ignoring since the scenario is tagged @WIP${System.lineSeparator()}${result.toReport().toText().prependIndent("  ")}"
                        throw TestAbortedException(message)
                    }
                    else -> ResultAssert.assertThat(result).isSuccess()
                }
            }
        }.toList()
    }

    private fun loadTestScenarios(
        path: String,
        suggestionsPath: String,
        suggestionsData: String,
        config: TestConfig
    ): List<ContractTest> {
        val contractFile = File(path)
        val feature = parseContractFileToFeature(contractFile.path).copy(testVariables = config.variables, testBaseURLs = config.baseURLs)

        val suggestions = when {
            suggestionsPath.isNotEmpty() -> suggestionsFromFile(suggestionsPath)
            suggestionsData.isNotEmpty() -> suggestionsFromCommandLine(suggestionsData)
            else -> emptyList()
        }

        return feature.generateContractTests(suggestions)
    }

    private fun suggestionsFromFile(suggestionsPath: String): List<Scenario> {
        return Suggestions.fromFile(suggestionsPath).scenarios
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
                        Row(columns, columns.map { row.getValue(it).toStringLiteral() })
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
