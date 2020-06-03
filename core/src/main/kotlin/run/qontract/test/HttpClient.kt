package run.qontract.test

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.forms.*
import io.ktor.client.request.request
import io.ktor.client.statement.readText
import io.ktor.client.statement.request
import io.ktor.client.utils.EmptyContent
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.toMap
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.runBlocking
import run.qontract.consoleLog
import run.qontract.core.*
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.StringPattern
import run.qontract.core.startLinesWith
import run.qontract.core.utilities.valueMapToPlainJsonString
import run.qontract.core.value.EmptyString
import run.qontract.core.value.Value
import run.qontract.stub.toParams
import java.io.File
import java.io.IOException
import java.net.URISyntaxException
import java.net.URL
import java.util.*

class HttpClient(private val baseURL: String, private val log: (event: String) -> Unit = ::consoleLog, private val ktorClient: HttpClient = HttpClient(CIO) { expectSuccess = false }) : TestExecutor {
    private val serverStateURL = "/_state_setup"

    @OptIn(KtorExperimentalAPI::class)
    @Throws(IOException::class, URISyntaxException::class)
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
                        .forEach { (key, loweredKey, value) ->
                            if(loweredKey !in listOfExcludedHeaders) {
                                this.headers[key] = value
                            }
                }

                when {
                    requestWithFileContent.formFields.isNotEmpty() -> {
                        val parameters = requestWithFileContent.formFields.mapValues { listOf(it.value) }.toList()
                        this.body = FormDataContent(parametersOf(*parameters.toTypedArray()))
                    }
                    requestWithFileContent.multiPartFormData.isNotEmpty() -> {
                        this.body = MultiPartFormDataContent(formData {
                            requestWithFileContent.multiPartFormData.forEach { value ->
                                when(value) {
                                    is MultiPartContentValue -> {
                                        append(value.name, value.content.toStringValue(), Headers.build {
                                            append(HttpHeaders.ContentType, ContentType.parse(value.content.httpContentType))
                                            append("Content-Disposition", "form-data; name=${value.name}")
                                        })
                                    }
                                    is MultiPartFileValue -> {
                                        appendInput(value.name, Headers.build {
                                            if(value.contentType != null)
                                                append(HttpHeaders.ContentType, ContentType.parse(value.contentType))
                                            value.contentEncoding?.let {
                                                append(HttpHeaders.ContentEncoding, value.contentEncoding)
                                            }
                                            append("Content-Disposition", "form-data; name=${value.name}; filename=${value.filename.removePrefix("@")}")
                                        }) {
                                            (value.content ?: "").byteInputStream().asInput()
                                        }
                                    }
                                }
                            }
                        })
                    }
                    requestWithFileContent.body != null -> {
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
            is TextContent -> Triple(qontractRequest.body ?: EmptyString, emptyMap<String, String>(), emptyList<MultiPartFormDataValue>())
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

private suspend fun ktorResponseToHttpResponse(ktorResponse: io.ktor.client.statement.HttpResponse): HttpResponse =
        HttpResponse(ktorResponse.status.value,
                try {
                    val data = ktorResponse.readText()
                    data
                } catch (e: ClientRequestException) {
                    val data = e.response.readText()
                    data
                },
                ktorResponse.headers.toMap().mapValues { it.value.first() }.toMutableMap())
