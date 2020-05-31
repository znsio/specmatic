package run.qontract.core

import run.qontract.core.pattern.StringPattern
import run.qontract.core.utilities.parseXML
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.NumberPattern
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue

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
        val request = HttpRequest().updateMethod("GET").updatePath("/balance").updateQueryParam("account_id", "10")
        val response = contractBehaviour.lookupResponse(request)
        Assertions.assertEquals(200, response.status)
        val root = parseXML(response.body?.displayableValue() ?: "").documentElement
        val type = root.attributes.getNamedItem("type").nodeValue
        val name = root.childNodes.item(0).childNodes.item(0).nodeValue
        val address = root.childNodes.item(1).childNodes.item(0).nodeValue
        val age = root.childNodes.item(2).childNodes.item(0).nodeValue
        Assertions.assertTrue(StringPattern.matches(StringValue(type), Resolver()) is Result.Success)
        Assertions.assertTrue(StringPattern.matches(StringValue(name), Resolver()) is Result.Success)
        Assertions.assertTrue(StringPattern.matches(StringValue(address), Resolver()) is Result.Success)
        Assertions.assertTrue(NumberPattern.matches(NumberValue(age.toInt()), Resolver()) is Result.Success)
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
        val request = HttpRequest().updateMethod("POST").updatePath("/balance").updateBody("<account type=\"user\"><name>John</name><address>Mumbai</address><age>25</age></account>")
        val response = contractBehaviour.lookupResponse(request)
        Assertions.assertEquals(200, response.status)
    }
}