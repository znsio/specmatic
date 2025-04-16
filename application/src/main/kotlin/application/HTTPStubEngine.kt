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
        baseUrl: String,
        certInfo: CertInfo,
        strictMode: Boolean,
        passThroughTargetBase: String = "",
        specmaticConfigPath: String? = null,
        httpClientFactory: HttpClientFactory,
        workingDirectory: WorkingDirectory,
        gracefulRestartTimeoutInMs: Long,
        specToBaseUrlMap: Map<String, String?>
    ): HttpStub {
        return HttpStub(
            features = stubs.map { it.first },
            rawHttpStubs = contractInfoToHttpExpectations(stubs),
            baseUrl = baseUrl,
            log = ::consoleLog,
            strictMode = strictMode,
            keyData = certInfo.getHttpsCert(),
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
                    serverStartupMessage(it.specToBaseUrlMap)
                )
            )
            consoleLog(StringLog("Press Ctrl + C to stop."))
        }
    }

    private fun serverStartupMessage(specToStubBaseUrlMap: Map<String, String>): String {
        val baseUrlToSpecs= specToStubBaseUrlMap.entries.groupBy({ it.value }, { it.key })

        return buildString {
            appendLine("Stub server is running on the following URLs:")
            baseUrlToSpecs.entries.sortedBy { it.key }.forEachIndexed { baseUrlIndex, (baseUrl, specs) ->
                appendLine("- $baseUrl serving endpoints from specs:")
                specs.sorted().forEachIndexed { specIndex, spec ->
                    appendLine("\t${specIndex.inc()}. $spec")
                }
                if (baseUrlIndex < baseUrlToSpecs.size - 1) appendLine()
            }
        }
    }
}
