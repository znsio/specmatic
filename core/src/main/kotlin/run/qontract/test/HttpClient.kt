package run.qontract.test

import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.ServerSetupStateException
import run.qontract.core.utilities.mapToJsonString
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.*

class HttpClient(private val baseURL: String) : TestExecutor {
    private val serverStateURL = "/_server_state"
    @UseExperimental(KtorExperimentalAPI::class)
    @Throws(IOException::class, URISyntaxException::class)
    override fun execute(request: HttpRequest): HttpResponse {
        val ktorClient = io.ktor.client.HttpClient(CIO)
        val url = URL(request.getURL(baseURL))

        return runBlocking<HttpResponse> {
            val ktorResponse: io.ktor.client.statement.HttpResponse = ktorClient.request(url) {
                this.method = io.ktor.http.HttpMethod.parse(request.method as String)

                if (request.headers.isNotEmpty()) {
                    this.headers {
                        for (header in request.headers) {
                            this[header.key as String] = header.value as String
                        }
                    }
                }

                if (request.body != null)
                    this.body = request.bodyString
            }

            HttpResponse(
                ktorResponse.status.value,
                try { ktorResponse.readText() } catch (e: io.ktor.client.features.ClientRequestException) { "" },
                ktorResponse.headers.toMap().mapValues { it.value.first() }.toMutableMap())
        }
    }

    @UseExperimental(KtorExperimentalAPI::class)
    @Throws(MalformedURLException::class, URISyntaxException::class, ServerSetupStateException::class)
    override fun setServerState(serverState: Map<String, Any?>) {
        if (serverState.isEmpty()) return

        val ktorClient = io.ktor.client.HttpClient(CIO)
        val url = URL(baseURL + serverStateURL)

        runBlocking {
            val ktorResponse: io.ktor.client.statement.HttpResponse = ktorClient.request(url) {
                this.method = io.ktor.http.HttpMethod.Post
                this.headers["Content-Type"] = "application/json"
                this.body = mapToJsonString(serverState)
            }

            if(ktorResponse.status != HttpStatusCode.OK)
                throw ServerSetupStateException("Server setup API responded with ${ktorResponse.status}")
        }
    }

}