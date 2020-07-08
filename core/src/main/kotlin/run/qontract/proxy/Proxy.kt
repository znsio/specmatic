package run.qontract.proxy

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import run.qontract.core.*
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.mock.ScenarioStub
import run.qontract.stub.ktorHttpRequestToHttpRequest
import run.qontract.stub.respondToKtorHttpResponse
import run.qontract.test.HttpClient
import java.io.Closeable
import java.io.File
import java.net.URL

class Proxy(host: String, port: Int, baseURL: String, private val proxyQontractDataDir: String, keyStoreData: KeyStoreData?): Closeable {
    private val stubs = mutableListOf<NamedStub>()

    private val environment = applicationEngineEnvironment {
        module {
            intercept(ApplicationCallPipeline.Call) {
                val httpRequest = ktorHttpRequestToHttpRequest(call)

                when(httpRequest.method?.toUpperCase()) {
                    "CONNECT" -> respondToKtorHttpResponse(call, HttpResponse(400, "CONNECT is not supported"))
                    else -> try {
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
        }

        when (keyStoreData) {
            null -> connector {
                this.host = host
                this.port = port
            }
            else -> sslConnector(
                    keyStore = keyStoreData.keyStore,
                    keyAlias = keyStoreData.keyAlias,
                    privateKeyPassword = { keyStoreData.keyPassword.toCharArray() },
                    keyStorePassword = { keyStoreData.keyPassword.toCharArray() }
            ) {
                this.host = host
                this.port = port
            }
        }
    }

    private val server: ApplicationEngine = embeddedServer(Netty, environment)

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

        if(stubs.isEmpty()) {
            println("No stubs were recorded. No contract will be written.")
        } else {
            val dataDir = File(proxyQontractDataDir)
            if (!dataDir.exists()) dataDir.mkdirs()

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
}
