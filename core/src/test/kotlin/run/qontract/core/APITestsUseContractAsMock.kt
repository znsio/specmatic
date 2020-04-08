package run.qontract.core

import com.intuit.karate.junit5.Karate
import run.qontract.mock.ContractMock
import run.qontract.mock.ContractMock.Companion.fromGherkin
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

internal class APITestsUseContractAsMock {
    @Karate.Test
    fun anAPITestShouldBeAbleToMockOutAService(): Karate {
        return Karate().relativeTo(javaClass).feature("classpath:APITestsUseContractAsMock.feature")
    }

    companion object {
        private var mock: ContractMock? = null
        @BeforeAll
        @Throws(Throwable::class)
        @JvmStatic
        fun setup() {
            val contractGherkin = "Feature: Contract for /balance API\n\n" +
                    "  Scenario: api call\n\n" +
                    "    When GET /balance?account_id=(number)\n" +
                    "    Then status 200\n" +
                    "    And response-body {calls_left: 10, messages_left: 20}"
            mock = fromGherkin(contractGherkin)
            mock!!.start()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            mock!!.close()
        }
    }
}