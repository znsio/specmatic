package run.qontract.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import run.qontract.core.Contract.Companion.forService
import run.qontract.core.Contract.Companion.fromGherkin
import run.qontract.core.utilities.brokerURL
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.IOException

class ContractTests {
    @Test
    @Throws(IOException::class)
    fun shouldBeAbleToGetContract() {
        val wireMockServer = WireMockServer(8089)
        wireMockServer.start()
        val majorVersion = 1
        val minorVersion = 0
        val mockResponseJSONObject: JSONObject = object : JSONObject() {
            init {
                put("majorVersion", majorVersion)
                put("minorVersion", minorVersion)
                put("spec", contractGherkin)
            }
        }
        val mapper = ObjectMapper()
        val jsonFactory = mapper.factory
        val jsonParser = jsonFactory.createParser(mockResponseJSONObject.toString())
        val mockResponse = mapper.readTree<JsonNode>(jsonParser)
        wireMockServer.stubFor(WireMock.get("/contracts?provider=balance&majorVersion=$majorVersion&minorVersion=$minorVersion").willReturn(WireMock.aResponse().withStatus(200).withJsonBody(mockResponse)))
        brokerURL = wireMockServer.baseUrl()
        val actual = forService("balance", majorVersion, minorVersion)
        val expected = fromGherkin(contractGherkin, majorVersion, minorVersion)
        Assertions.assertEquals(expected, actual)
        wireMockServer.stop()
    }

    @Test
    @Throws(Throwable::class)
    fun shouldBeAbleToGetFakeFromContract() {
        val contract = fromGherkin(contractGherkin, 1, 0)
        contract.startFake(8080).close()
    }

    @Test
    @Throws(Throwable::class)
    fun shouldBeAbleToRunTestFromContract() {
        val contract = fromGherkin(contractGherkin, 1, 0)
        contract.startFake(8080).use { fake -> contract.test(fake.endPoint) }
    }

    @Test
    @Throws(Throwable::class)
    fun shouldBeAbleToTestFakeObject() {
        val contract = fromGherkin(contractGherkin, 1, 0)
        contract.startFake(8080).use { fake -> contract.test(fake) }
    }

    @Test
    @Throws(Throwable::class)
    fun shouldBeAbleToTestFakeObjectWithPath() {
        val contract = fromGherkin(pathParameterContractGherkin, 1, 0)
        contract.startFake(8080).use { fake -> contract.test(fake) }
    }

    companion object {
        var contractGherkin = """
            Feature: Contract for the balance service
            
                Background:
                | userid |
                | 12345 |
                
                Scenario: Should be able to get the balance for an individual
                  Given fact userid
                  When GET /balance?userid=(number)
                  Then status 200
                  And response-header Content-Type application/json
                  And response-header Content-Length (number)
                  And response-body {call-mins-left: "(number)", sms-messages-left: "(number)"}
                  
                Scenario: Should be able to get the balance for an individual
                  Given fact no_user
                  When GET /balance?userid=(number)
                  Then status 404
                  And response-header Content-Type application/json
                  And response-header Content-Length (number)
                """
        var pathParameterContractGherkin = """
                Feature: Contract for the balance service
                
                Background:
                | userid |
                | 12345  |
                
                Scenario: Should be able to get the balance for an individual
                  Given fact userid
                  When GET /balance/(userid:number)
                  Then status 200
                  And response-header Content-Type application/json
                  And response-header Content-Length (number)
                  And response-body {call-mins-left: "(number)", sms-messages-left: "(number)"}
                """
    }
}