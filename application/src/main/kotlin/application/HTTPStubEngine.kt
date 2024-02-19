package application

import `in`.specmatic.core.Feature
import `in`.specmatic.core.WorkingDirectory
import `in`.specmatic.core.log.NewLineLogMessage
import `in`.specmatic.core.log.StringLog
import `in`.specmatic.core.log.consoleLog
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpClientFactory
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.stub.contractInfoToHttpExpectations
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
            specmaticConfigPath = specmaticConfigPath
        ).also {
            consoleLog(NewLineLogMessage)
            val protocol = if (keyStoreData != null) "https" else "http"
            consoleLog(StringLog("Stub server is running on ${protocol}://$host:$port. Ctrl + C to stop."))
        }
    }
}
