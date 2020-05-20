package run.qontract.test

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Examples
import run.qontract.core.pattern.parsedValue
import run.qontract.core.utilities.readFile
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue
import java.io.File
import java.time.Duration
import java.util.*
import kotlin.system.exitProcess

val pass = Unit

open class QontractJUnitSupport {
    @TestFactory()
    fun contractAsTest(): Collection<DynamicTest> {
        val testScenarios: List<Scenario>
        val path = System.getProperty("path")

        val suggestions = System.getProperty("suggestions") ?: ""
        val suggestionsPath = System.getProperty("suggestionsPath") ?: ""
        val checkBackwardCompatibility = (System.getProperty("checkBackwardCompatibility") ?: "false").toBoolean()

        if(checkBackwardCompatibility) {
            checkBackwardCompatibilityInPath(path)
        }

        val contractGherkin = try {
            readFile(path)
        } catch (exception: Throwable) {
            println("Exception (Class=${exception.javaClass.name}, Message=${exception.message ?: exception.localizedMessage})")
            throw exception
        }

        val contractBehaviour = try {
            ContractBehaviour(contractGherkin)
        } catch (exception: Throwable) {
            println("Exception (Class=${exception.javaClass.name}, Message=${exception.message ?: exception.localizedMessage})")
            throw exception
        }

        testScenarios = when {
            suggestionsPath.isNotEmpty() -> {
                val suggestionsGherkin = readFile(suggestionsPath)
                contractBehaviour.generateTestScenarios(Suggestions(suggestionsGherkin).scenarios)
            }
            suggestions.isNotEmpty() -> {
                val suggestionsValue = parsedValue(suggestions)
                if(suggestionsValue !is JSONObjectValue)
                    throw ContractException("Suggestions must be a json value with scenario name as the key, and json array with 1 or more json objects containing suggestions")

                val exampleScenarios = suggestionsValue.jsonObject.mapValues { (_, exampleData) ->
                    if(exampleData !is JSONArrayValue)
                        throw ContractException("The value of a scenario must be a list of examples")

                    if(exampleData.list.size == 0)
                        Examples()
                    else {
                        val firstRow = exampleData.list.get(0)
                        if(firstRow !is JSONObjectValue)
                            throw ContractException("Each value in the list of suggestions must be a json object containing column name as key and sample value as the value")

                        val columns = firstRow.jsonObject.keys.toList()

                        Examples(columns.toMutableList()).apply {
                            for(row in exampleData.list) {
                                if(row !is JSONObjectValue)
                                    throw ContractException("Each value in the list of suggestions must be a json object containing column name as key and sample value as the value")

                                val rowValues = columns.map { row.jsonObject.getValue(it).toStringValue() }
                                this.addRow(rowValues)
                            }
                        }
                    }
                }.entries.map { (name, examples) ->
                    Scenario(name, HttpRequestPattern(), HttpResponsePattern(), emptyMap(), listOf(examples), emptyMap(), emptyMap(), null)
                }

                contractBehaviour.generateTestScenarios(exampleScenarios)
            }
            else -> contractBehaviour.generateTestScenarios(LinkedList())
        }

        return testScenarios.map {
            DynamicTest.dynamicTest("$it") {
                val asyncMessage = it.kafkaMessagePattern

                when {
                    asyncMessage != null -> {
                        if (System.getProperty("kafkaPort") == null) {
                            println("The contract has a kafka message. Please specify the port of the Kafka instance to connect to.")
                            exitProcess(1)
                        }

                        val commit = "true" == System.getProperty("commit")

                        createConsumer(getBootstrapKafkaServers(), commit).use { consumer ->
                            val topic = it.kafkaMessagePattern?.topic ?: ""
                            consumer.subscribe(listOf(topic))

                            val messages = consumer.poll(Duration.ofSeconds(1)).map {
                                KafkaMessage(topic, it.key()?.let { key -> StringValue(key) }, parsedValue(it.value()))
                            }

                            val result = asyncMessage.matches(messages.single(), it.resolver)
                            ResultAssert.assertThat(result).isSuccess()
                        }
                    }
                    else -> {
                        val host = System.getProperty("host")
                        val port = System.getProperty("port")
                        val protocol = System.getProperty("protocol") ?: "http"

                        val httpClient = HttpClient("$protocol://$host:$port")

                        val request = it.generateHttpRequest()
                        var response: HttpResponse? = null

                        val result: Result = try {
                            httpClient.setServerState(it.serverState)
                            response = httpClient.execute(request)
                            when (response.status) {
                                400 -> Result.Failure(response.body?.displayableValue()
                                        ?: "").also { failureResult -> failureResult.updateScenario(it) }
                                else -> it.matches(response)
                            }
                        } catch (exception: Throwable) {
                            Result.Failure("Exception (Class=${exception.javaClass.name}, Message=${exception.message ?: exception.localizedMessage})")
                                    .also { failure -> failure.updateScenario(it) }
                        }

                        ResultAssert.assertThat(result).isSuccess(request, response)
                    }
                }
            }
        }.toList()
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
            if (!path.endsWith(".$CONTRACT_EXTENSION"))
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
