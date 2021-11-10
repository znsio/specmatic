package application

import `in`.specmatic.core.*
import `in`.specmatic.core.log.StringLog
import `in`.specmatic.core.log.consoleLog
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.contractInfoToKafkaExpectations
import `in`.specmatic.stub.stubKafkaContracts
import org.springframework.stereotype.Component

@Component
class KafkaStubEngine {
    fun runKafkaStub(stubs: List<Pair<Feature, List<ScenarioStub>>>, kafkaHost: String, kafkaPort: Int, startKafka: Boolean): QontractKafka? {
        val features = stubs.map { it.first }

        val kafkaExpectations = contractInfoToKafkaExpectations(stubs)
        val validationResults = kafkaExpectations.map { stubData ->
            features.asSequence().map {
                it.matchesMockKafkaMessage(stubData.kafkaMessage)
            }.find {
                it is Result.Failure
            } ?: Result.Success()
        }

        return when {
            validationResults.any { it is Result.Failure } -> {
                val results = Results(validationResults.toMutableList())
                consoleLog(StringLog("Can't load Kafka mocks:\n${results.report(PATH_NOT_RECOGNIZED_ERROR)}"))
                null
            }
            hasKafkaScenarios(features) -> {
                val qontractKafka = when {
                    startKafka -> {
                        println("Starting local Kafka server...")
                        QontractKafka(kafkaPort).also {
                            consoleLog(StringLog("Started local Kafka server: ${it.bootstrapServers}"))
                        }
                    }
                    else -> null
                }

                stubKafkaContracts(
                    kafkaExpectations, qontractKafka?.bootstrapServers
                        ?: "PLAINTEXT://$kafkaHost:$kafkaPort", ::createTopics, ::createProducer
                )

                qontractKafka
            }
            else -> null
        }
    }
}

internal fun hasKafkaScenarios(behaviours: List<Feature>): Boolean {
    return behaviours.any {
        it.scenarios.any { scenario ->
            scenario.kafkaMessagePattern != null
        }
    }
}
