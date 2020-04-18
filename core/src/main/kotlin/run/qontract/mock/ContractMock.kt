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
import io.ktor.response.header
import io.ktor.response.respondText
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import run.qontract.core.value.*
import org.w3c.dom.Document
import org.xml.sax.SAXException
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.EmptyString
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
                respondAndDelete(call, expectations, httpRequest)
        }
    }

    val baseURL = "http://localhost" + if(port == 80) "" else ":$port"
    val mockSetupURL = "$baseURL/_mock_setup"

    private fun registerExpectation(call: ApplicationCall, httpRequest: HttpRequest) {
        try {
            createMockScenario(stringToMockScenario(httpRequest.body ?: throw ContractException("Expectation payload was empty")))

            call.response.status(HttpStatusCode.OK)
        }
        catch(e: NoMatchingScenario) {
            writeBadRequest(call, e.message)
        }
        catch(e: ContractException) {
            writeBadRequest(call, e.message)
        }
        catch (e: Exception) {
            writeBadRequest(call, e.message)
        }
    }

    fun createMockScenario(mocked: MockScenario) {
        val mockedResponse = contractBehaviour.matchingMockResponse(mocked.request, mocked.response)
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
        while(true) {
            Thread.sleep(1000)

            try {
                if (!server.application.isActive) break
            } catch(e: Exception) {
                println(e.localizedMessage)
            }
        }
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

fun stringToMockScenario(text: Value): MockScenario {
    val mockSpec =
            jsonStringToValueMap(text.toStringValue()).also {
                validateMock(it)
            }

    return mockFromJSON(mockSpec)
}

fun matchesRequest(actual: HttpRequest, expected: HttpRequest) =
        expected.method == actual.method &&
                actual.path == expected.path &&
                actual.queryParams == expected.queryParams &&
                matchesBody(actual, expected)

fun respondAndDelete(call: ApplicationCall, expectations: MutableList<Pair<HttpRequest, HttpResponse>>, httpRequest: HttpRequest) {
    expectations.find {
        matchesRequest(httpRequest, it.first)
    }?.let {
        respondToKtorHttpResponse(call, it.second)
        expectations.remove(it)
    } ?: {
        respondToKtorHttpResponse(call, debugInfoIn400Response(httpRequest, expectations))
    }()
}

fun debugInfoIn400Response(httpRequest: HttpRequest, expectations: List<Pair<HttpRequest, HttpResponse>>): HttpResponse {
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

fun matchesBody(actual: HttpRequest, expected: HttpRequest): Boolean {
    when(parsedValue(actual.body.toString())) {
        is JSONObjectValue, is JSONArrayValue -> {
            if(expected.body != actual.body) return false
        }
        is XMLValue -> {
            try {
                val mockedXML = expected.body as Document
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
            if(expected.body is EmptyString && actual.body is StringValue) {
                return actual.bodyString.isEmpty()
            } else if(expected.body is StringValue && actual.body is EmptyString) {
                return expected.bodyString.isEmpty()
            }
            else if(expected.body?.toStringValue() != actual.body?.toStringValue()) {
                return false
            }
        }
    }

    return true
}

fun writeBadRequest(call: ApplicationCall, errorMessage: String?) {
    call.response.status(HttpStatusCode.UnprocessableEntity)
    call.response.header("X-Qontract-Result", "failure")
    runBlocking { call.respondText(errorMessage ?: "") }
}
