package `in`.specmatic.core

import `in`.specmatic.core.pattern.parsedJSONObject
import `in`.specmatic.test.HttpClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContractIntegrationTest {
    @Test
    fun shouldInvokeTargetUsingServerState() {
        val contractGherkin = "" +
                "Feature: Contract for /balance API\n\n" +
                "  Scenario: api call\n\n" +
                "    Given fact user jack\n" +
                "    When POST /accounts\n" +
                "    And request-body {name: \"jack\", address: \"Mumbai\"}\n" +
                "    Then status 409\n" +
                ""

        val server = embeddedServer(Netty, port = 8080) {
            routing {
                post("/_specmatic/state") {
                    val requestBody: String = call.receive<String>()
                    val requestJSON = parsedJSONObject(requestBody)

                    assertThat(requestJSON.jsonObject["user"]?.toStringLiteral()).isEqualTo("jack")

                    call.respond("")
                }

                post("/accounts") {
                    val requestBody: String = call.receive<String>()
                    val requestJSON = parsedJSONObject(requestBody)

                    assertThat(requestJSON.jsonObject["name"]?.toStringLiteral()).isEqualTo("jack")
                    assertThat(requestJSON.jsonObject["address"]?.toStringLiteral()).isEqualTo("Mumbai")
                    call.respond(HttpStatusCode.Conflict, "")
                }
            }
        }

        try {
            server.start(wait = false)

            val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
            val results = contractBehaviour.executeTests(HttpClient("http://localhost:8080"))
            assertThat(results.success()).withFailMessage(results.report()).isTrue()
        } finally {
            server.stop()
        }
    }
}