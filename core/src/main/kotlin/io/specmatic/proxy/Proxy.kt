package io.specmatic.proxy

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.specmatic.core.*
import io.specmatic.core.route.modules.HealthCheckModule.Companion.configureHealthCheckModule
import io.specmatic.core.route.modules.HealthCheckModule.Companion.isHealthCheckRequest
import io.specmatic.core.log.logger
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.httpRequestLog
import io.specmatic.stub.httpResponseLog
import io.specmatic.stub.ktorHttpRequestToHttpRequest
import io.specmatic.stub.respondToKtorHttpResponse
import io.specmatic.test.HttpClient
import io.swagger.v3.core.util.Yaml
import java.io.Closeable
import java.net.URI
import java.net.URL

class Proxy(host: String, port: Int, baseURL: String, private val outputDirectory: FileWriter, keyData: KeyData? = null): Closeable {
    constructor(host: String, port: Int, baseURL: String, proxySpecmaticDataDir: String, keyData: KeyData? = null) : this(host, port, baseURL, RealFileWriter(proxySpecmaticDataDir), keyData)

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

                    if(httpRequest.isHealthCheckRequest()) return@intercept

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
                                "${httpRequest.method} ${httpRequest.path}${toQueryString(httpRequest.queryParams.asMap())}"
                            stubs.add(
                                NamedStub(
                                    name,
                                    getShortNameForNamedStub(httpRequest, baseURL, httpResponse.status),
                                    ScenarioStub(
                                        httpRequest.withoutDynamicHeaders(),
                                        httpResponse.withoutDynamicHeaders()
                                    )
                                )
                            )

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

            configureHealthCheckModule()
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

    private fun toQueryString(queryParams: Map<String, String>): String {
        return queryParams.entries.joinToString("&") { entry ->
            "${entry.key}=${entry.value}"
        }.let { when {
            it.isEmpty() -> it
            else -> "?$it"
        }}
    }

    private fun getShortNameForNamedStub(httpRequest: HttpRequest, baseURL: String, responseStatus: Int): String {
        val (method, path) = httpRequest
        val formattedPath = path?.replace(baseURL, "")
            ?.replace("/", ".")
            ?.drop(1)
            .orEmpty()
        if (formattedPath.isEmpty()) return "$method-$responseStatus"
        return "$formattedPath-$method-$responseStatus"
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
        return path != null && try {
            URL(URLParts(path).withEncodedPathSegments()); true } catch(e: Throwable) { false }
    }

    init {
        server.start()
    }

    override fun close() {
        server.stop(0, 0)

        val gherkin = toGherkinFeature("New feature", stubs)
        val base = "proxy_generated"
        val featureFileName = "$base.yaml"

        if(stubs.isEmpty()) {
            println("No stubs were recorded. No contract will be written.")
        } else {
            outputDirectory.createDirectory()

            val stubDataDirectory = outputDirectory.subDirectory("${base}$EXAMPLES_DIR_SUFFIX")
            stubDataDirectory.createDirectory()

            stubs.mapIndexed { index, namedStub ->
                val fileName = "${namedStub.shortName}-${index.inc()}.json"
                println("Writing stub data to $fileName")
                stubDataDirectory.writeText(fileName, namedStub.stub.toJSON().toStringLiteral())
            }

            val openApi = parseGherkinStringToFeature(gherkin).toOpenApi()

            println("Writing specification to $featureFileName")
            outputDirectory.writeText(featureFileName, Yaml.pretty(openApi))

        }
    }
}
