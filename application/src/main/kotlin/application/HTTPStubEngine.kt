package application

import io.specmatic.core.Feature
import io.specmatic.core.WorkingDirectory
import io.specmatic.core.log.NewLineLogMessage
import io.specmatic.core.log.StringLog
import io.specmatic.core.log.consoleLog
import io.specmatic.core.utilities.consolePrintableURL
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.HttpClientFactory
import io.specmatic.stub.HttpStub
import io.specmatic.stub.contractInfoToHttpExpectations
import org.springframework.stereotype.Component

@Component
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
        gracefulRestartTimeoutInMs: Long
    ): HttpStub? {
        val features = stubs.map { it.first }

        val httpExpectations = contractInfoToHttpExpectations(stubs)

        val keyStoreData = certInfo.getHttpsCert()

        return HttpStub(
            features,
            httpExpectations,
            host,
            port,
            ::consoleLog,
            strictMode,
            keyStoreData,
            passThroughTargetBase = passThroughTargetBase,
            httpClientFactory = httpClientFactory,
            workingDirectory = workingDirectory,
            specmaticConfigPath = specmaticConfigPath,
            timeoutMillis = gracefulRestartTimeoutInMs
        ).also {
            consoleLog(NewLineLogMessage)
            consoleLog(StringLog("Stub server is running on ${consolePrintableURL(host, port, keyStoreData)}. Ctrl + C to stop."))
        }
    }
}
