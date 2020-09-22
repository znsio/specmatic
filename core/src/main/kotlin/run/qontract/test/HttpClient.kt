package run.qontract.test

import io.ktor.client.HttpClient
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.runBlocking
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.ssl.SSLContextBuilder
import run.qontract.consoleLog
import run.qontract.core.*
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.StringPattern
import run.qontract.core.utilities.valueMapToPlainJsonString
import run.qontract.core.value.EmptyString
import run.qontract.core.value.Value
import run.qontract.stub.toParams
import java.io.File
import java.net.URL
import java.util.*

class HttpClient(private val baseURL: String, private val timeout: Int = 60, private val log: (event: String) -> Unit = ::consoleLog, private val ktorClient: HttpClient = HttpClient(io.ktor.client.engine.apache.Apache) {
    expectSuccess = false

    engine {
        customizeClient {
            setSSLContext(
                    SSLContextBuilder
                            .create()
                            .loadTrustMaterial(TrustSelfSignedStrategy())
                            .build()
            )
            setSSLHostnameVerifier(NoopHostnameVerifier())
        }
    }

    install(HttpTimeout) {
        requestTimeoutMillis = (timeout * 1000).toLong()
    }
}) : TestExecutor {
    private val serverStateURL = "/_qontract/state"

    @OptIn(KtorExperimentalAPI::class)
    override fun execute(request: HttpRequest): HttpResponse {
        val url = URL(request.getURL(baseURL))

        val startTime = Date()

        val requestWithFileContent = loadFileContentIntoParts(request)

        return runBlocking {
            val ktorResponse: io.ktor.client.statement.HttpResponse = ktorClient.request(url) {
                this.method = HttpMethod.parse(requestWithFileContent.method as String)

                val listOfExcludedHeaders = HttpHeaders.UnsafeHeadersList.map { it.toLowerCase() }

                requestWithFileContent.headers
                        .map {Triple(it.key.trim(), it.key.trim().toLowerCase(), it.value.trim())}
                        .filter { (_, loweredKey, _) -> loweredKey !in listOfExcludedHeaders }
                        .forEach { (key, _, value) ->
                            this.headers[key] = value
                }

                when {
                    requestWithFileContent.formFields.isNotEmpty() -> {
                        val parameters = requestWithFileContent.formFields.mapValues { listOf(it.value) }.toList()
                        this.body = FormDataContent(parametersOf(*parameters.toTypedArray()))
                    }
                    requestWithFileContent.multiPartFormData.isNotEmpty() -> {
                        this.body = MultiPartFormDataContent(formData {
                            requestWithFileContent.multiPartFormData.forEach { value ->
                                value.addTo(this)
                            }
                        })
                    }
                    else -> {
                        this.body = when {
                            requestWithFileContent.headers.containsKey("Content-Type") -> TextContent(requestWithFileContent.bodyString, ContentType.parse(requestWithFileContent.headers["Content-Type"] as String))
                            else -> TextContent(requestWithFileContent.bodyString, ContentType.parse(requestWithFileContent.body.httpContentType))
                        }
                    }
                }
            }

            val endTime = Date()

            val outboundRequest: HttpRequest = ktorHttpRequestToHttpRequest(ktorResponse.request, requestWithFileContent)
            log(">> Request Start At $startTime")
            log(outboundRequest.toLogString("-> "))

            ktorResponseToHttpResponse(ktorResponse).also {
                log(it.toLogString("<- "))
                log("<< Response At $endTime == ")
                log(System.lineSeparator())
            }
        }
    }

    private fun loadFileContentIntoParts(request: HttpRequest): HttpRequest {
        val parts = request.multiPartFormData

        val newParts = parts.map { part ->
            when(part) {
                is MultiPartContentValue -> part
                is MultiPartFileValue -> {
                    val partFile = File(part.filename.removePrefix("@"))
                    val content = if(partFile.exists()) {
                        partFile.readText()
                    } else {
                        StringPattern.generate(Resolver()).toStringValue()
                    }
                    part.copy(content = content)
                }
            }
        }

        return request.copy(multiPartFormData = newParts)
    }

    @OptIn(KtorExperimentalAPI::class)
    override fun setServerState(serverState: Map<String, Value>) {
        if (serverState.isEmpty()) return

        val url = URL(baseURL + serverStateURL)

        val startTime = Date()

        runBlocking {
            log("# >> Request Sent At $startTime")
            log(startLinesWith(valueMapToPlainJsonString(serverState), "# "))

            val ktorResponse: io.ktor.client.statement.HttpResponse = ktorClient.request(url) {
                this.method = HttpMethod.Post
                this.contentType(ContentType.Application.Json)
                this.body = valueMapToPlainJsonString(serverState)
            }

            val endTime = Date()

            log("# << Complete At $endTime")

            ktorResponseToHttpResponse(ktorResponse)

            if(ktorResponse.status != HttpStatusCode.OK)
                throw Exception("API responded with ${ktorResponse.status}")
        }
    }
}

private fun ktorHttpRequestToHttpRequest(request: io.ktor.client.request.HttpRequest, qontractRequest: HttpRequest): HttpRequest {
    val(body, formFields, multiPartFormData) =
        when(request.content) {
            is FormDataContent -> Triple(EmptyString, qontractRequest.formFields, emptyList())
            is TextContent -> Triple(qontractRequest.body, emptyMap(), emptyList())
            is MultiPartFormDataContent -> Triple(EmptyString, emptyMap(), qontractRequest.multiPartFormData)
            is EmptyContent -> Triple(EmptyString, emptyMap(), emptyList())
            else -> throw ContractException("Unknown type of body content sent in the request")
        }

    val requestHeaders = request.headers.toMap().mapValues { it.value[0] }

    return HttpRequest(method = request.method.value,
            path = request.url.encodedPath,
            headers = requestHeaders,
            body = body,
            queryParams = toParams(request.url.parameters),
            formFields = formFields,
            multiPartFormData = multiPartFormData)
}

private suspend fun ktorResponseToHttpResponse(ktorResponse: io.ktor.client.statement.HttpResponse): HttpResponse {
    val encoding = ktorResponse.headers.get("Content-Encoding")

    return HttpResponse(ktorResponse.status.value,
            try {
                decodeData(ktorResponse.readBytes(), encoding, ktorResponse.charset())
            } catch (e: ClientRequestException) {
                e.response?.let { response ->
                    decodeData(response.readBytes(), encoding, ktorResponse.charset())
                } ?: ""
            },
            ktorResponse.headers.toMap().mapValues { it.value.first() }.toMutableMap())
}

private fun decodeData(bytes: ByteArray, encoding: String?, receivedCharset: Charset?): String {
    val charset = Charset.forName(receivedCharset?.name() ?: "UTF-8")

    return when(encoding) {
        "gzip" -> java.util.zip.GZIPInputStream(bytes.inputStream()).bufferedReader(charset).use {
            it.readText()
        }
        else -> String(bytes)
    }
}