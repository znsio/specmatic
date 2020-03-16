package run.qontract.core

import com.intuit.karate.junit5.Karate
import run.qontract.fake.ContractFake
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class ContractFakeShouldRunOverTCP {
    @Karate.Test
    fun fakeShouldServeSingleFeatureContract(): Karate {
        return Karate().relativeTo(javaClass).feature("classpath:ContractFakeShouldRunOverTCP.feature")
    }

    companion object {
        var contractFake: ContractFake? = null
        @BeforeAll
        @Throws(Throwable::class)
        @JvmStatic
        fun startContractServerWithSingleFeature() {
            val data = "Feature: Contract for /balance API\n\n" +
                    "  Scenario: \n\n" +
                    "    When GET /balance?account_id=10\n" +
                    "    Then status 200\n" +
                    "    And response-body {calls_left: 10, messages_left: 20}\n\n" +
                    "  Scenario: JSON API to get list of locations\n" +
                    "    Given pattern Location {\"id\": \"(number)\", \"city\": \"(string)\"}\n" +
                    "    When GET /locations_json\n" +
                    "    Then status 200\n" +
                    "    And response-body {\"locations\": [\"(Location*)\"]}\n" +
                    "  \n" +
                    "  Scenario: JSON API to create locations\n" +
                    "    Given pattern Location {\"city\": \"(string)\"}\n" +
                    "    When POST /locations_json\n" +
                    "    And request-body {\"locations\": [\"(Location*)\"]}\n" +
                    "    Then status 200\n" +
                    "  Scenario: XML API to get list of locations\n" +
                    "    Given pattern Location <city><id>(number)</id><name>(string)</name></city>\n" +
                    "    When GET /locations_xml\n" +
                    "    Then status 200\n" +
                    "    And response-body <locations>(Location*)</locations>\n" +
                    "  \n" +
                    "  Scenario: XML API to create locations\n" +
                    "    Given pattern Location <city><id>(number)</id><name>(string)</name></city>\n" +
                    "    When POST /locations_xml\n" +
                    "    And request-body <locations>(Location*)</locations>\n" +
                    "    Then status 200\n" +
                    "  Scenario: \n\n" +
                    "    Given fact account_id 10\n" +
                    "    When GET /balance?account_id=10\n" +
                    "    Then status 200\n" +
                    "    And response-body {calls_left: 10, messages_left: 20}"
            contractFake = ContractFake(data, "127.0.0.1", 8080)
        }

        @AfterAll
        @JvmStatic
        fun stopServer() {
            contractFake!!.close()
        }
    }
}