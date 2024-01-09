package `in`.specmatic.proxy

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import `in`.specmatic.core.*
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.httpRequestLog
import `in`.specmatic.stub.httpResponseLog
import `in`.specmatic.stub.ktorHttpRequestToHttpRequest
import `in`.specmatic.stub.respondToKtorHttpResponse
import `in`.specmatic.test.HttpClient
import io.swagger.v3.core.util.Yaml
import java.io.Closeable
import java.net.URI
import java.net.URL

class Proxy(host: String, port: Int, baseURL: String, private val outputDirectory: FileWriter, keyData: KeyData? = null): Closeable {
    constructor(host: String, port: Int, baseURL: String, proxyQontractDataDir: String, keyData: KeyData? = null) : this(host, port, baseURL, RealFileWriter(proxyQontractDataDir), keyData)

    private val stubs = mutableListOf<NamedStub>()

    private val targetHost = baseURL.let {
        when {
            it.isBlank() -> null
            else -> URI(baseURL).host
        }
    }

    private val environment = applicationEngineEnvironment {
        module {
            intercept(ApplicationCallPipeline.Call) {
                try {
                    val httpRequest = ktorHttpRequestToHttpRequest(call)

                    when (httpRequest.method?.uppercase()) {
                        "CONNECT" -> {
                            val errorResponse = HttpResponse(400, "CONNECT is not supported")
                            println(
                                listOf(httpRequestLog(httpRequest), httpResponseLog(errorResponse)).joinToString(
                                    System.lineSeparator()
                                )
                            )
                            respondToKtorHttpResponse(call, errorResponse)
                        }

                        else -> try {
                            val client = HttpClient(proxyURL(httpRequest, baseURL))

                            val requestToSend = targetHost?.let {
                                httpRequest.withHost(targetHost)
                            } ?: httpRequest

                            val httpResponse = client.execute(requestToSend)

                            val name =
                                "${httpRequest.method} ${httpRequest.path}${toQueryString(httpRequest.queryParams)}"
                            stubs.add(NamedStub(name, ScenarioStub(httpRequest.withoutDynamicHeaders(), httpResponse.withoutDynamicHeaders())))

                            respondToKtorHttpResponse(call, withoutContentEncodingGzip(httpResponse))
                        } catch (e: Throwable) {
                            logger.log(e)
                            val errorResponse =
                                HttpResponse(500, exceptionCauseMessage(e) + "\n\n" + e.stackTraceToString())
                            respondToKtorHttpResponse(call, errorResponse)
                            logger.debug(
                                listOf(
                                    httpRequestLog(httpRequest),
                                    httpResponseLog(errorResponse)
                                ).joinToString(System.lineSeparator())
                            )
                        }
                    }
                } catch (e: Throwable) {
                    logger.log(e)
                    val errorResponse =
                        HttpResponse(500, exceptionCauseMessage(e) + "\n\n" + e.stackTraceToString())
                    respondToKtorHttpResponse(call, errorResponse)
                }
            }
        }

        when (keyData) {
            null -> connector {
                this.host = host
                this.port = port
            }
            else -> sslConnector(keyStore = keyData.keyStore, keyAlias = keyData.keyAlias, privateKeyPassword = { keyData.keyPassword.toCharArray() }, keyStorePassword = { keyData.keyPassword.toCharArray() }) {
                this.host = host
                this.port = port
            }
        }
    }

    private fun withoutContentEncodingGzip(httpResponse: HttpResponse): HttpResponse {
        val contentEncodingKey = httpResponse.headers.keys.find { it.lowercase() == "content-encoding" } ?: "Content-Encoding"
        return when {
            httpResponse.headers[contentEncodingKey]?.lowercase()?.contains("gzip") == true ->
                httpResponse.copy(headers = httpResponse.headers.minus(contentEncodingKey))
            else ->
                httpResponse
        }
    }

    private val server: ApplicationEngine = embeddedServer(Netty, environment, configure = {
        this.requestQueueLimit = 1000
        this.callGroupSize = 5
        this.connectionGroupSize = 20
        this.workerGroupSize = 20
    })

    private fun proxyURL(httpRequest: HttpRequest, baseURL: String): String {
        return when {
            isFullURL(httpRequest.path) -> ""
            else -> baseURL
        }
    }

    private fun isFullURL(path: String?): Boolean {
        return path != null && try { URL(path.uriEscape()); true } catch(e: Throwable) { false }
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
        val base = "proxy_generated"
        val featureFileName = "$base.yaml"
        val openApi = parseGherkinStringToFeature(gherkin).toOpenApi()

        if(stubs.isEmpty()) {
            println("No stubs were recorded. No contract will be written.")
        } else {
            outputDirectory.createDirectory()

            println("Writing contract to $featureFileName")
            outputDirectory.writeText(featureFileName, Yaml.pretty(openApi))

            val stubDataDirectory = outputDirectory.subDirectory("${base}_data")
            stubDataDirectory.createDirectory()

            stubs.mapIndexed { index, namedStub ->
                val fileName = "stub$index.json"
                println("Writing stub data to $fileName")
                stubDataDirectory.writeText(fileName, namedStub.stub.toJSON().toStringLiteral())
            }
        }
    }
}
