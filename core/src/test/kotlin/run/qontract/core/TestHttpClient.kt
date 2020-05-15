package run.qontract.core

import run.qontract.fake.ContractFake
import run.qontract.test.HttpClient
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.util.*

class TestHttpClient {
    @Test
    @Throws(Throwable::class)
    fun clientShouldGenerateRequestAndParseResponse() {
        val request = HttpRequest().updateMethod("POST").updatePath("/balance").updateQueryParam("account-id", "10").updateBody("{name: \"Sherlock\", address: \"221 Baker Street\"}")
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
        ContractFake(contractGherkin, emptyList(), host, port).use {
            val response = client.execute(request)
            Assertions.assertNotNull(response)
            Assertions.assertEquals(200, response.status)
            val jsonResponseBody = JSONObject(response.body?.toStringValue())
            Assertions.assertEquals(10, jsonResponseBody.getInt("location-id"))
        }
    }

    @Test
    @Throws(Throwable::class)
    fun clientShouldPerformServerSetup() {
        val request = HttpRequest().updateMethod("POST").updatePath("/balance").updateQueryParam("account-id", "10").updateBody("{name: \"Sherlock\", address: \"221 Baker Street\"}")
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

        ContractFake(contractGherkin, emptyList(), host, port).use {
            client.setServerState(mapOf("server" to StringValue("state")))
            val response = client.execute(request)
            Assertions.assertNotNull(response)
            Assertions.assertEquals(200, response.status)
            val jsonResponseBody = JSONObject(response.body?.toStringValue())
            Assertions.assertEquals(10, jsonResponseBody.getInt("location-id"))
        }
    }
}