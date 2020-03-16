package run.qontract.core

import run.qontract.core.pattern.NumericStringPattern
import run.qontract.core.pattern.StringPattern
import run.qontract.core.pattern.asValue
import run.qontract.core.utilities.parseXML
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class XMLMatching {
    @Test
    @Throws(Throwable::class)
    fun theContractShouldSupportXMLResponseGeneration() {
        val contractGherkin = "" +
                "Feature: Unit test\n\n" +
                "  Scenario: Unit test\n" +
                "    When GET /balance?account_id=(number)\n" +
                "    Then status 200\n" +
                "    And response-body <account type=\"(string)\"><name>(string)</name><address>(string)</address><age>(number)</age></account>\n" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val request = HttpRequest().setMethod("GET").updatePath("/balance").setQueryParam("account_id", "10")
        val response = contractBehaviour.lookup(request)
        Assertions.assertEquals(200, response.status)
        val root = parseXML(response.body ?: "").documentElement
        val type = root.attributes.getNamedItem("type").nodeValue
        val name = root.childNodes.item(0).childNodes.item(0).nodeValue
        val address = root.childNodes.item(1).childNodes.item(0).nodeValue
        val age = root.childNodes.item(2).childNodes.item(0).nodeValue
        Assertions.assertTrue(StringPattern().matches(asValue(type), Resolver()) is Result.Success)
        Assertions.assertTrue(StringPattern().matches(asValue(name), Resolver()) is Result.Success)
        Assertions.assertTrue(StringPattern().matches(asValue(address), Resolver()) is Result.Success)
        Assertions.assertTrue(NumericStringPattern().matches(asValue(age), Resolver()) is Result.Success)
    }

    @Test
    @Throws(Throwable::class)
    fun theContractShouldSupportXMLRequestMatching() {
        val contractGherkin = "" +
                "Feature: Unit test\n\n" +
                "  Scenario: Unit test\n" +
                "    When POST /balance\n" +
                "    And request-body <account type=\"(string)\"><name>(string)</name><address>(string)</address><age>(number)</age></account>\n" +
                "    Then status 200\n" +
                ""
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val request = HttpRequest().setMethod("POST").updatePath("/balance").setBody("<account type=\"user\"><name>John</name><address>Mumbai</address><age>25</age></account>")
        val response = contractBehaviour.lookup(request)
        Assertions.assertEquals(200, response.status)
    }
}