package run.qontract.proxy

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.NamedStub
import run.qontract.core.toGherkinFeature
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.mock.ScenarioStub
import run.qontract.stub.ktorHttpRequestToHttpRequest
import run.qontract.stub.respondToKtorHttpResponse
import run.qontract.test.HttpClient
import java.io.Closeable
import java.io.File
import java.net.URL

class Proxy(host: String, port: Int, baseURL: String, private val proxyQontractDataDir: String): Closeable {
    private val stubs = mutableListOf<NamedStub>()

    private val server: ApplicationEngine = embeddedServer(Netty, host = host, port = port) {
        intercept(ApplicationCallPipeline.Call) {
            val httpRequest = ktorHttpRequestToHttpRequest(call)

            try {
                val client = HttpClient(proxyURL(httpRequest, baseURL))

                val httpResponse = client.execute(httpRequest)

                val name = "${httpRequest.method} ${httpRequest.path}${toQueryString(httpRequest.queryParams)}"
                stubs.add(NamedStub(name, ScenarioStub(httpRequest, httpResponse)))

                respondToKtorHttpResponse(call, httpResponse)
            } catch(e: Throwable) {
                println(exceptionCauseMessage(e))
                respondToKtorHttpResponse(call, HttpResponse(500, exceptionCauseMessage(e)))
            }
        }
    }

    private fun proxyURL(httpRequest: HttpRequest, baseURL: String): String {
        return when {
            isFullURL(httpRequest.path) -> ""
            else -> baseURL
        }
    }

    private fun isFullURL(path: String?): Boolean {
        return path != null && try { URL(path); true } catch(e: Throwable) { false }
    }

    init {
        server.start()
    }

    private fun toQueryString(queryParams: Map<String, String>): String {
        return queryParams.entries.joinToString("&") { entry ->
            "${entry.key}=${entry.value}"
        }.let { when {
            it.isEmpty() -> it
            else -> "?$it"
        }}
    }

    override fun close() {
        server.stop(0, 0)

        val gherkin = toGherkinFeature("New feature", stubs)

        val dataDir = File(proxyQontractDataDir)
        if(!dataDir.exists()) dataDir.mkdirs()

        val contractFile = dataDir.resolve("new_feature.qontract")
        println("Writing contract to ${contractFile.path}")
        contractFile.writeText(gherkin)

        stubs.mapIndexed() { index, namedStub ->
            val stubFile = dataDir.resolve("stub$index.json")
            println("Writing stub data to ${stubFile.path}")
            stubFile.writeText(namedStub.stub.toJSON().toStringValue())
        }
    }
}
