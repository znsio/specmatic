package application

import io.specmatic.core.Feature
import io.specmatic.core.WorkingDirectory
import io.specmatic.core.log.NewLineLogMessage
import io.specmatic.core.log.StringLog
import io.specmatic.core.log.consoleLog
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.HttpClientFactory
import io.specmatic.stub.HttpStub
import io.specmatic.stub.contractInfoToHttpExpectations
import io.specmatic.stub.endPointFromHostAndPort

class HTTPStubEngine {
    fun runHTTPStub(
        stubs: List<Pair<Feature, List<ScenarioStub>>>,
        host: String,
        port: Int,
        certInfo: CertInfo,
        strictMode: Boolean,
        passThroughTargetBase: String = "",
        specmaticConfigPath: String? = null,
        httpClientFactory: HttpClientFactory,
        workingDirectory: WorkingDirectory,
        gracefulRestartTimeoutInMs: Long,
        specToBaseUrlMap: Map<String, String?>
    ): HttpStub {
        val keyData = certInfo.getHttpsCert()

        return HttpStub(
            features = stubs.map { it.first },
            rawHttpStubs = contractInfoToHttpExpectations(stubs),
            host = host,
            port = port,
            log = ::consoleLog,
            strictMode = strictMode,
            keyData = keyData,
            passThroughTargetBase = passThroughTargetBase,
            httpClientFactory = httpClientFactory,
            workingDirectory = workingDirectory,
            specmaticConfigPath = specmaticConfigPath,
            timeoutMillis = gracefulRestartTimeoutInMs,
            specToStubBaseUrlMap = specToBaseUrlMap
        ).also {
            consoleLog(NewLineLogMessage)
            consoleLog(
                StringLog(
                    serverStartupMessage(
                        specToBaseUrlMap,
                        endPointFromHostAndPort(host, port, keyData)
                    )
                )
            )
            consoleLog(StringLog("Press Ctrl + C to stop."))
        }
    }

    private fun serverStartupMessage(
        specToStubBaseUrlMap: Map<String, String?>,
        defaultBaseUrl: String
    ): String {
        val newLine = System.lineSeparator()
        val baseUrlToSpecs: Map<String, List<String>> = specToStubBaseUrlMap.entries
            .groupBy({ it.value ?: defaultBaseUrl }, { it.key })

        val messageBuilder = StringBuilder("Stub server is running on the following URLs:")

        baseUrlToSpecs.entries
            .sortedBy { it.key }
            .forEach { (baseUrl, specs) ->
                messageBuilder.append("${newLine}- $baseUrl serving endpoints from specs:")
                specs.sorted().forEachIndexed { index, spec ->
                    messageBuilder.append("$newLine    ${index.inc()}. $spec")
                }
                messageBuilder.append(newLine)
            }

        return messageBuilder.toString()
    }
}
