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
        specToPortMap: Map<String, Int?>
    ): HttpStub {
        return HttpStub(
            features = stubs.map { it.first },
            rawHttpStubs = contractInfoToHttpExpectations(stubs),
            host = host,
            port = port,
            log = ::consoleLog,
            strictMode = strictMode,
            keyData = certInfo.getHttpsCert(),
            passThroughTargetBase = passThroughTargetBase,
            httpClientFactory = httpClientFactory,
            workingDirectory = workingDirectory,
            specmaticConfigPath = specmaticConfigPath,
            timeoutMillis = gracefulRestartTimeoutInMs,
            specToStubPortMap = specToPortMap
        ).also {
            consoleLog(NewLineLogMessage)
            consoleLog(StringLog(serverStartupMessage(specToPortMap, port)))
            consoleLog(StringLog("Press Ctrl + C to stop."))
        }
    }

    private fun serverStartupMessage(specToStubPortMap: Map<String, Int?>, defaultPort: Int): String {
        val newLine = System.lineSeparator()
        val portToSpecs: Map<Int, List<String>> = specToStubPortMap.entries
            .groupBy({ it.value ?: defaultPort }, { it.key })

        val messageBuilder = StringBuilder("Stub server is running on the following URLs:")

        portToSpecs.entries
            .sortedBy { it.key }
            .forEach { (port, specs) ->
                messageBuilder.append("${newLine}- http://localhost:$port serving endpoints from specs:")
                specs.sorted().forEachIndexed { index, spec ->
                    messageBuilder.append("$newLine    ${index.inc()}. $spec")
                }
                messageBuilder.append(newLine)
            }

        return messageBuilder.toString()
    }
}
