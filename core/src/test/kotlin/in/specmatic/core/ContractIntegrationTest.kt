package `in`.specmatic.core

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import `in`.specmatic.test.HttpClient
import junit.framework.Assert.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ContractIntegrationTest {
    @Test
    @Throws(Throwable::class)
    fun shouldInvokeTargetUsingServerState() {
        val contractGherkin = "" +
                "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    Given fact user jack\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"jack\", address: \"Mumbai\"}\n" +
                "    Then status 409\n" +
                ""
        wireMockServer.stubFor(WireMock.post("/_specmatic/state").withRequestBody(WireMock.equalToJson("{\"user\": \"jack\"}")).willReturn(WireMock.aResponse().withStatus(200)))
        wireMockServer.stubFor(WireMock.post("/accounts").withRequestBody(WireMock.equalToJson("{\"name\": \"jack\", \"address\": \"Mumbai\"}")).willReturn(WireMock.aResponse().withStatus(409)))
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val results = contractBehaviour.executeTests(HttpClient(wireMockServer.baseUrl()))
        assertTrue(results.report(), results.success())
    }

    companion object {
        private val wireMockServer = WireMockServer()

        @JvmStatic
        @BeforeAll
        fun startWiremock() {
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopWiremock() {
            wireMockServer.stop()
        }
    }
}