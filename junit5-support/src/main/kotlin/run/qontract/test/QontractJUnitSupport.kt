package run.qontract.test

import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedValue
import run.qontract.core.utilities.readFile
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

        val suggestionsPath = System.getProperty("suggestions")
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
            suggestionsPath.isNullOrEmpty() -> contractBehaviour.generateTestScenarios(LinkedList())
            else -> {
                val suggestionsGherkin = readFile(suggestionsPath)
                val suggestions = Suggestions(suggestionsGherkin).scenarios
                contractBehaviour.generateTestScenarios(suggestions)
            }
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

                        val kafkaPort = System.getProperty("kafkaPort").toInt()

                        createConsumer("PLAINTEXT://localhost:$kafkaPort").use { consumer ->
                            val topic = it.kafkaMessagePattern?.target ?: ""
                            consumer.assign(listOf(TopicPartition(topic, 0)))
                            consumer.poll(Duration.ofSeconds(1))
                            consumer.seekToBeginning(listOf(TopicPartition(topic, 0)))

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
