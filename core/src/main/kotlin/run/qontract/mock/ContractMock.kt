package run.qontract.mock

import run.qontract.core.ContractBehaviour
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.utilities.*
import run.qontract.fake.ktorHttpRequestToHttpRequest
import run.qontract.fake.respondToKtorHttpResponse
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import run.qontract.core.value.*
import org.w3c.dom.Document
import org.xml.sax.SAXException
import run.qontract.core.pattern.parsedValue
import java.io.Closeable
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

class ContractMock(contractGherkin: String, port: Int) : Closeable {
    private val contractBehaviour: ContractBehaviour = ContractBehaviour(contractGherkin)
    private val expectations: MutableList<Pair<HttpRequest, HttpResponse>> = mutableListOf()

    private val server: ApplicationEngine = embeddedServer(Netty, port) {
        intercept(ApplicationCallPipeline.Call) {
            val httpRequest = ktorHttpRequestToHttpRequest(call)

            if(isMockRequest(httpRequest))
                 registerExpectation(call, httpRequest)
            else
                responseIfExpected(call, httpRequest)
        }
    }

    val baseURL = "http://localhost" + if(port == 80) "" else ":$port"
    val mockSetupURL = "$baseURL/_mock_setup"

    private fun responseIfExpected(call: ApplicationCall, httpRequest: HttpRequest) {
        expectations.find { it ->
            matches(httpRequest, it.first)
        }?.let {
            respondToKtorHttpResponse(call, it.second)
            expectations.remove(it)
        } ?: {
            respondToKtorHttpResponse(call, debugInfoIn400Response(httpRequest, expectations))
        }()
    }

    private fun debugInfoIn400Response(httpRequest: HttpRequest, expectations: MutableList<Pair<HttpRequest, HttpResponse>>): HttpResponse {
        val expectationsString = expectations.mapIndexed { index, expectation -> """
Expectation $index
  Request: ${expectation.first.toJSON()}
  Response: ${expectation.second.toJSON()}
""".trimIndent()}.joinToString(System.lineSeparator())

        val message = """
Message received: ${httpRequest.toJSON()}

$expectationsString
"""
        return HttpResponse(400, message)
    }

    private fun registerExpectation(call: ApplicationCall, httpRequest: HttpRequest) {
        try {
            validateHttpMockRequest(httpRequest)
            val mockSpec = jsonStringToMap(httpRequest.body.toString()).also {
                MockScenario.validate(it)
            }

            createMockScenario(MockScenario.fromJSON(mockSpec))

            call.response.status(HttpStatusCode.OK)
        } catch (e: Exception) {
            call.response.status(HttpStatusCode.BadRequest)
            runBlocking { call.respondText(e.message ?: "") }
        }
    }

    private fun matches(actual: HttpRequest, expected: HttpRequest) =
        expected.method == actual.method &&
        actual.path == expected.path &&
        actual.queryParams == expected.queryParams &&
        matchesBody(actual, expected)

    private fun matchesBody(actual: HttpRequest, expected: HttpRequest): Boolean {
        when(parsedValue(actual.body.toString())) {
            is JSONObjectValue, is JSONArrayValue -> {
                if(expected.body != actual.body) return false
            }
            is XMLValue -> {
                try {
                    val mockedXML = expected.body?.value as Document
                    val requestXML = parseXML(actual.body?.toString() ?: "")

                    return mockedXML == requestXML
                } catch (e: ParserConfigurationException) {
                    e.printStackTrace()
                } catch (e: SAXException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                return false
            }
            else -> {
                if(expected.body is NoValue && actual.body is StringValue) {
                    return actual.bodyString.isEmpty()
                } else if(expected.body is StringValue && actual.body is NoValue) {
                    return expected.bodyString.isEmpty()
                }
                else if(expected.body != actual.body) {
                    return false
                }
            }
        }

        return true
    }

    private fun validateHttpMockRequest(request: HttpRequest) =
        when (request.body) {
            null -> throw Exception("Mock request body can't be empty")
            else -> true
        }

    fun createMockScenario(mocked: MockScenario) {
        contractBehaviour.setServerState(mocked.facts)

        val mockedResponse = contractBehaviour.getResponseForMock(mocked.request, mocked.response)
        expectations.add(Pair(mocked.request, mockedResponse))
    }

    private fun isMockRequest(httpRequest: HttpRequest) =
        httpRequest.path == "/_mock_setup" && httpRequest.method == "POST"

    override fun close() {
        server.stop(0, 5000)
    }

    fun start() {
        server.start()
    }

    @Throws(InterruptedException::class)
    fun waitUntilClosed() {
        while(server.application.isActive) Thread.sleep(1000)
    }

    companion object {
        @JvmStatic
        @Throws(Throwable::class)
        fun fromGherkin(contractGherkin: String, port: Int = 8080): ContractMock {
            return ContractMock(contractGherkin, port)
        }

        @JvmStatic
        fun fromGherkin(contractGherkin: String): ContractMock {
            return ContractMock(contractGherkin, 8080)
        }
    }
}