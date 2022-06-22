package application

import `in`.specmatic.core.Feature
import `in`.specmatic.core.log.StringLog
import `in`.specmatic.core.log.consoleLog
import `in`.specmatic.core.log.logger
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.JMSStub
import org.springframework.stereotype.Component

@Component
class JMSStubEngine {
    fun runJMSStub(stubs: List<Pair<Feature, List<ScenarioStub>>>): JMSStub? {
        val features = stubs.map { it.first }

        return when {
            hasJmsScenarios(features) -> {
                JMSStub(features).also {
                    consoleLog(StringLog("JMS Stub server is running on ${it.brokerUrl()}. Ctrl + C to stop."))
                }
            }
            else -> {
                logger.log("Could not find any JMS contracts, so stub server not started.")
                null
            }
        }
    }

    private fun hasJmsScenarios(features: List<Feature>): Boolean =
        features.any { feature -> feature.scenarios.any { it.async } }
}