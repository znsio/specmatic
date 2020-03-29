package run.qontract.test

import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.FormPart
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.request
import io.ktor.client.statement.readText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.ServerSetupStateException
import run.qontract.core.utilities.nativeMapToJsonString
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL

class HttpClient(private val baseURL: String) : TestExecutor {
    private val serverStateURL = "/_server_state"
    @KtorExperimentalAPI
    @Throws(IOException::class, URISyntaxException::class)
    override fun execute(request: HttpRequest): HttpResponse {
        val ktorClient = io.ktor.client.HttpClient(CIO)
        val url = URL(request.getURL(baseURL))

        return runBlocking {
            val ktorResponse: io.ktor.client.statement.HttpResponse = ktorClient.request(url) {
                this.method = io.ktor.http.HttpMethod.parse(request.method as String)

                val listOfExcludedHeaders = listOf("content-type", "content-length")
                request.headers
                        .map {Triple(it.key?.trim() ?: "", it.key?.trim()?.toLowerCase() ?: "", it.value?.trim() ?: "")}
                        .forEach { (key, loweredKey, value) ->
                            if(loweredKey !in listOfExcludedHeaders) {
                                this.headers[key] = value
                            }
                }

                if(request.formFields.isNotEmpty()) {
                    request.formFields.forEach { (key, value) ->
                        this.body = MultiPartFormDataContent(formData {
                            this.append(FormPart(key, value))
                        })
                    }
                }
                else if (request.body != null) {
                    this.body = when {
                        request.headers.containsKey("Content-Type") -> TextContent(request.bodyString, ContentType.parse(request.headers["Content-Type"] as String))
                        else -> request.bodyString
                    }
                }
            }

            HttpResponse(
                ktorResponse.status.value,
                try { ktorResponse.readText() } catch (e: io.ktor.client.features.ClientRequestException) { "" },
                ktorResponse.headers.toMap().mapValues { it.value.first() }.toMutableMap())
        }
    }

    @KtorExperimentalAPI
    @Throws(MalformedURLException::class, URISyntaxException::class, ServerSetupStateException::class)
    override fun setServerState(serverState: Map<String, Any?>) {
        if (serverState.isEmpty()) return

        val ktorClient = io.ktor.client.HttpClient(CIO)
        val url = URL(baseURL + serverStateURL)

        runBlocking {
            val ktorResponse: io.ktor.client.statement.HttpResponse = ktorClient.request(url) {
                this.method = io.ktor.http.HttpMethod.Post
                this.contentType(ContentType.Application.Json)
                this.body = nativeMapToJsonString(serverState)
            }

            if(ktorResponse.status != HttpStatusCode.OK)
                throw ServerSetupStateException("Server setup API responded with ${ktorResponse.status}")
        }
    }

}