package `in`.specmatic.core

import com.intuit.karate.junit5.Karate
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import `in`.specmatic.stub.HttpStub

internal class APITestsUseContractAsMock {
    @Karate.Test
    fun anAPITestShouldBeAbleToMockOutAService(): Karate {
        return Karate.run("classpath:APITestsUseContractAsMock.feature").relativeTo(javaClass)
    }

    companion object {
        private lateinit var stub: HttpStub

        @BeforeAll
        @Throws(Throwable::class)
        @JvmStatic
        fun setup() {
            val contractGherkin = "Feature: Contract for /balance API\n\n" +
                    "  Scenario: api call\n\n" +
                    "    When GET /balance?account_id=(number)\n" +
                    "    Then status 200\n" +
                    "    And response-body {calls_left: 10, messages_left: 20}"
            stub = HttpStub(contractGherkin, port = 8080)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            stub.close()
        }
    }
}