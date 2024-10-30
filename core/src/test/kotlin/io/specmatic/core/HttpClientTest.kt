package io.specmatic.core

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.specmatic.core.value.StringValue
import io.specmatic.stub.HttpStub
import io.specmatic.test.HttpClient
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class HttpClientTest {

    @Test
    fun clientShouldNotRedirect() {
        val server = embeddedServer(Netty, port = 8080) {
            routing {
                get("/some/redirect") {
                    call.respondRedirect("/newUrl")
                }

                get("/newUrl") {
                    call.respond("")
                }
            }
        }

        try {
            server.start(wait = false)

            val request = HttpRequest().updateMethod("GET").updatePath("/some/redirect")
            val response = HttpClient("http://localhost:8080").execute(request)
            Assertions.assertEquals(302, response.status)
            Assertions.assertEquals("/newUrl", response.headers["Location"])
        } finally {
            server.stop()
        }
    }

    @Test
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