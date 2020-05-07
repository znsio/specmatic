package run.qontract.test

import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.forms.*
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.readText
import io.ktor.client.statement.request
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.TextContent
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.appendFiltered
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
import run.qontract.fake.toParams
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.URISyntaxException
import java.net.URL
import java.util.*

class HttpClient(private val baseURL: String, private val log: (event: String) -> Unit = ::consoleLog) : TestExecutor {
    private val serverStateURL = "/_server_state"
    @OptIn(KtorExperimentalAPI::class)
    @Throws(IOException::class, URISyntaxException::class)
    override fun execute(request: HttpRequest): HttpResponse {
        val ktorClient = io.ktor.client.HttpClient(CIO) { expectSuccess = false }
        val url = URL(request.getURL(baseURL))

        val startTime = Date()

        return runBlocking {
            val ktorResponse: io.ktor.client.statement.HttpResponse = ktorClient.request(url) {
                this.method = HttpMethod.parse(request.method as String)

                val listOfExcludedHeaders = HttpHeaders.UnsafeHeadersList.map { it.toLowerCase() }

                request.headers
                        .map {Triple(it.key.trim(), it.key.trim().toLowerCase(), it.value.trim())}
                        .forEach { (key, loweredKey, value) ->
                            if(loweredKey !in listOfExcludedHeaders) {
                                this.headers[key] = value
                            }
                }

                when {
                    request.formFields.isNotEmpty() -> {
                        val parameters = request.formFields.mapValues { listOf(it.value) }.toList()
                        this.body = FormDataContent(parametersOf(*parameters.toTypedArray()))
                    }
                    request.multiPartFormData.isNotEmpty() -> {
                        this.body = MultiPartFormDataContent(formData {
                            request.multiPartFormData.forEach { value ->
                                when(value) {
                                    is MultiPartContentValue -> {
                                        append(value.name, value.content.toStringValue(), Headers.build {
                                            this.append(HttpHeaders.ContentType, ContentType.parse(value.content.httpContentType))
                                            this.append("Content-Disposition", "form-data; name=${value.name}")
                                        })
                                    }
                                    is MultiPartFileValue -> {
                                        appendInput(value.name, Headers.build {
                                            this.append(HttpHeaders.ContentType, ContentType.parse(value.contentType))
                                            value.contentEncoding?.let {
                                                this.append(HttpHeaders.ContentEncoding, value.contentEncoding)
                                            }
                                            this.append("Content-Disposition", "form-data; name=${value.name}; filename=${value.filename.removePrefix("@")}")
                                        }) {
                                            val partFile = File(value.filename.removePrefix("@"))
                                            if(partFile.exists()) {
                                                partFile.inputStream().asInput()
                                            } else {
                                                val random = StringPattern.generate(Resolver()).toStringValue()
                                                random.byteInputStream().asInput()
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    }
                    request.body != null -> {
                        this.body = when {
                            request.headers.containsKey("Content-Type") -> TextContent(request.bodyString, ContentType.parse(request.headers["Content-Type"] as String))
                            else -> TextContent(request.bodyString, ContentType.parse(request.body.httpContentType))
                        }
                    }
                }
            }

            val endTime = Date()

            val outboundRequest: HttpRequest = ktorHttpRequestToHttpRequest(ktorResponse.request, request)
            log(">> Request Start At $startTime")
            log(outboundRequest.toLogString("-> "))

            ktorResponseToHttpResponse(ktorResponse).also {
                log(it.toLogString("<- "))
                log("<< Response At $endTime == ")
                log(System.lineSeparator())
            }
        }
    }

    @OptIn(KtorExperimentalAPI::class)
    override fun setServerState(serverState: Map<String, Value>) {
        if (serverState.isEmpty()) return

        val ktorClient = io.ktor.client.HttpClient(CIO)
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
            is MultiPartFormDataContent -> Triple(EmptyString, emptyMap<String, String>(), qontractRequest.multiPartFormData)
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
