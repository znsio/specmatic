package `in`.specmatic.test

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
import `in`.specmatic.consoleLog
import `in`.specmatic.core.*
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.StringPattern
import `in`.specmatic.core.utilities.valueMapToPlainJsonString
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.Value
import `in`.specmatic.core.HttpLogMessage
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.stub.getDateStringValue
import `in`.specmatic.stub.toParams
import java.io.File
import java.net.URL
import java.util.*
import java.util.zip.GZIPInputStream

// API for non-Kotlin invokers
fun createHttpClient(baseURL: String, timeout: Int) = HttpClient(baseURL, timeout)

class HttpClient(val baseURL: String, private val timeout: Int = 60, private val log: (event: LogMessage) -> Unit = ::consoleLog, private val ktorClient: HttpClient = HttpClient(io.ktor.client.engine.apache.Apache) {
    expectSuccess = false

    followRedirects = false

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
    private val serverStateURL = "/_$APPLICATION_NAME_LOWER_CASE/state"

    override fun execute(request: HttpRequest): HttpResponse {
        val url = URL(request.getURL(baseURL))

        val startTime = getDateStringValue()

        val requestWithFileContent = loadFileContentIntoParts(request)

        return runBlocking {
            val ktorResponse: io.ktor.client.statement.HttpResponse = ktorClient.request(url) {
                this.method = HttpMethod.parse(requestWithFileContent.method as String)

                val listOfExcludedHeaders = HttpHeaders.UnsafeHeadersList.map { it.lowercase() }

                requestWithFileContent.headers
                        .map {Triple(it.key.trim(), it.key.trim().lowercase(), it.value.trim())}
                        .filter { (_, loweredKey, _) -> loweredKey !in listOfExcludedHeaders }
                        .forEach { (key, _, value) ->
                            this.header(key, value)
                }

                this.body = when {
                    requestWithFileContent.formFields.isNotEmpty() -> {
                        val parameters = requestWithFileContent.formFields.mapValues { listOf(it.value) }.toList()
                        FormDataContent(parametersOf(*parameters.toTypedArray()))
                    }
                    requestWithFileContent.multiPartFormData.isNotEmpty() -> {
                        MultiPartFormDataContent(formData {
                            requestWithFileContent.multiPartFormData.forEach { value ->
                                value.addTo(this)
                            }
                        })
                    }
                    else -> {
                        when {
                            requestWithFileContent.headers.containsKey("Content-Type") -> TextContent(requestWithFileContent.bodyString, ContentType.parse(requestWithFileContent.headers["Content-Type"] as String))
                            else -> TextContent(requestWithFileContent.bodyString, ContentType.parse(requestWithFileContent.body.httpContentType))
                        }
                    }
                }
            }

            val endTime = getDateStringValue()

            val httpLogMessage = HttpLogMessage()

            val outboundRequest: HttpRequest = ktorHttpRequestToHttpRequest(ktorResponse.request, requestWithFileContent)
            httpLogMessage.addRequest(outboundRequest, startTime)

            ktorResponseToHttpResponse(ktorResponse).also {
                httpLogMessage.addResponse(it, endTime)
                log(httpLogMessage)
            }
        }
    }

    private fun loadFileContentIntoParts(request: HttpRequest): HttpRequest {
        val parts = request.multiPartFormData

        val newMultiPartFormData: List<MultiPartFormDataValue> = parts.map { part ->
            when(part) {
                is MultiPartContentValue -> part
                is MultiPartFileValue -> {
                    val partFile = File(part.filename.removePrefix("@"))
                    val binaryContent = if(partFile.exists()) {
                        MultiPartContent(partFile)
                    } else {
                        MultiPartContent(StringPattern().generate(Resolver()).toStringLiteral())
                    }
                    part.copy(content = binaryContent)
                }
            }
        }

        return request.copy(multiPartFormData = newMultiPartFormData)
    }

    override fun setServerState(serverState: Map<String, Value>) {
        if (serverState.isEmpty()) return

        val url = URL(baseURL + serverStateURL)

        val startTime = Date()

        runBlocking {
            var endTime: Date? = null
            var response: HttpResponse? = null

            try {
                val ktorResponse: io.ktor.client.statement.HttpResponse = ktorClient.request(url) {
                    this.method = HttpMethod.Post
                    this.contentType(ContentType.Application.Json)
                    this.body = valueMapToPlainJsonString(serverState)
                }

                endTime = Date()

                response = ktorResponseToHttpResponse(ktorResponse)

                if (ktorResponse.status != HttpStatusCode.OK)
                    throw Exception("API responded with ${ktorResponse.status}")
            } finally {
                val serverStateLog = object: LogMessage {
                    override fun toJSONObject(): JSONObjectValue {
                        val data: MutableMap<String, String> = mutableMapOf(
                            "requestTime" to startTime.toString(),
                            "serverState" to valueMapToPlainJsonString(serverState)
                        )

                        if(endTime != null && response != null) {
                            data["endTime"] = endTime.toString()
                            data["response"] = response.toLogString()
                        }

                        return JSONObjectValue(data.mapValues { StringValue(it.value) }.toMap())
                    }

                    override fun toLogString(): String {

                        return """
                        # >> Request Sent At $startTime
                        ${startLinesWith(valueMapToPlainJsonString(serverState), "# ")}
                        "# << Complete At $endTime"
                        """.trimIndent()
                    }
                }

                log(serverStateLog)
            }
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

suspend fun ktorResponseToHttpResponse(ktorResponse: io.ktor.client.statement.HttpResponse): HttpResponse {
    val (headers, body) = decodeBody(ktorResponse)
    return HttpResponse(ktorResponse.status.value, body, headers)
}

suspend fun decodeBody(ktorResponse: io.ktor.client.statement.HttpResponse): Pair<Map<String, String>, String> {
    val encoding = ktorResponse.headers["Content-Encoding"]
    val headers = ktorResponse.headers.toMap().mapValues { it.value.first() }

    return try {
        decodeBody(ktorResponse.readBytes(), encoding, ktorResponse.charset(), headers)
    } catch (e: ClientRequestException) {
        decodeBody(e.response.readBytes(), encoding, ktorResponse.charset(), headers)
    }
}

fun decodeBody(bytes: ByteArray, encoding: String?, receivedCharset: Charset?, headers: Map<String, String>): Pair<Map<String, String>, String> =
    when(encoding) {
        "gzip" -> {
            Pair(
                headers.minus("Content-Encoding"),
                unzip(bytes, receivedCharset))
        }
        else -> Pair(headers, String(bytes))
    }

fun unzip(bytes: ByteArray, receivedCharset: Charset?): String {
    val charset = Charset.forName(receivedCharset?.name() ?: "UTF-8")
    return GZIPInputStream(bytes.inputStream()).bufferedReader(charset).use { it.readText() }
}
