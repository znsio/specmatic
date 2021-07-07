package `in`.specmatic.core

import com.github.tomakehurst.wiremock.client.WireMock.*
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.test.HttpClient
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.StringValue
import com.github.tomakehurst.wiremock.WireMockServer

class HttpClientTest {

    @Test
    fun clientShouldNotRedirect() {
        val wireMockServer = WireMockServer()
        wireMockServer.start()
        stubFor(
            get(urlEqualTo("/some/redirect")).willReturn(
                aResponse().withStatus(302).withHeader("Location", "/newUrl")
            )
        )
        stubFor(
            get(urlEqualTo("/newUrl")).willReturn(aResponse().withStatus(200))
        )
        val request = HttpRequest().updateMethod("GET").updatePath("/some/redirect")
        val response = HttpClient("http://localhost:8080").execute(request)
        Assertions.assertEquals(302, response.status)
        Assertions.assertEquals("/newUrl", response.headers.get("Location"))

        wireMockServer.stop()
    }

    @Test
    @Throws(Throwable::class)
    fun clientShouldGenerateRequestAndParseResponse() {
        val request = HttpRequest().updateMethod("POST").updatePath("/balance").updateQueryParam("account-id", "10")
            .updateBody("{name: \"Sherlock\", address: \"221 Baker Street\"}")
        val contractGherkin = "" +
                "Feature: Unit test\n\n" +
                "  Scenario: Unit test\n" +
                "    Given POST /balance?account-id=(number)\n" +
                "    When request-body {name: \"(string)\", address: \"(string)\"}\n" +
                "    Then status 200\n" +
                "    And response-body {location-id: 10}"
        val host = "localhost"
        val port = 8080
        val url = "http://localhost:$port"
        val client = HttpClient(url)
        HttpStub(contractGherkin, emptyList(), host, port).use {
            val response = client.execute(request)
            Assertions.assertNotNull(response)
            Assertions.assertEquals(200, response.status)
            val jsonResponseBody = JSONObject(response.body.toStringLiteral())
            Assertions.assertEquals(10, jsonResponseBody.getInt("location-id"))
        }
    }

    @Test
    @Throws(Throwable::class)
    fun clientShouldPerformServerSetup() {
        val request = HttpRequest().updateMethod("POST").updatePath("/balance").updateQueryParam("account-id", "10")
            .updateBody("{name: \"Sherlock\", address: \"221 Baker Street\"}")
        val contractGherkin = "" +
                "Feature: Unit test\n\n" +
                "  Scenario: Unit test\n" +
                "    Given fact server state\n" +
                "    When POST /balance?account-id=(number)\n" +
                "    And request-body {name: \"(string)\", address: \"(string)\"}\n" +
                "    Then status 200\n" +
                "    And response-body {location-id: 10}"
        val host = "localhost"
        val port = 8080
        val url = "http://localhost:$port"
        val client = HttpClient(url)

        HttpStub(contractGherkin, emptyList(), host, port).use {
            client.setServerState(mapOf("server" to StringValue("state")))
            val response = client.execute(request)
            Assertions.assertNotNull(response)
            Assertions.assertEquals(200, response.status)
            val jsonResponseBody = JSONObject(response.body.toStringLiteral())
            Assertions.assertEquals(10, jsonResponseBody.getInt("location-id"))
        }
    }
}