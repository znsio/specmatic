package application

import `in`.specmatic.core.Feature
import `in`.specmatic.core.WorkingDirectory
import `in`.specmatic.core.log.StringLog
import `in`.specmatic.core.log.consoleLog
import `in`.specmatic.core.log.logger
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpClientFactory
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.stub.contractInfoToHttpExpectations
import org.springframework.stereotype.Component

@Component
class HTTPStubEngine {
    fun runHTTPStub(stubs: List<Pair<Feature, List<ScenarioStub>>>, host: String, port: Int, certInfo: CertInfo, strictMode: Boolean, passThroughTargetBase: String = "", httpClientFactory: HttpClientFactory, workingDirectory: WorkingDirectory): HttpStub? {
        val features = stubs.map { it.first }

        return when {
            hasHttpScenarios(features) -> {
                val httpExpectations = contractInfoToHttpExpectations(stubs)

                val httpFeatures = features.map {
                    val httpScenarios = it.scenarios.filter { scenario ->
                        scenario.kafkaMessagePattern == null
                    }

                    it.copy(scenarios = httpScenarios)
                }

                val keyStoreData = certInfo.getHttpsCert()
                HttpStub(
                    httpFeatures,
                    httpExpectations,
                    host,
                    port,
                    ::consoleLog,
                    strictMode,
                    keyStoreData,
                    passThroughTargetBase = passThroughTargetBase,
                    httpClientFactory = httpClientFactory,
                    workingDirectory = workingDirectory
                ).also {
                    val protocol = if (keyStoreData != null) "https" else "http"
                    consoleLog(StringLog("Stub server is running on ${protocol}://$host:$port. Ctrl + C to stop."))
                }
            }
            else -> {
                logger.log("Could not find any HTTP contracts, so stub server not started.")
                null
            }
        }
    }
}

internal fun hasHttpScenarios(behaviours: List<Feature>): Boolean {
    return behaviours.any {
        it.scenarios.any { scenario ->
            scenario.kafkaMessagePattern == null // && !scenario.async
        }
    }
}
