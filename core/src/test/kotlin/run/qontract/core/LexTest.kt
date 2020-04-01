package run.qontract.core

import org.junit.jupiter.api.Test
import run.qontract.core.pattern.NumericStringPattern
import run.qontract.core.value.NumberValue
import kotlin.contracts.contract
import kotlin.test.assertEquals
import kotlin.test.fail

class LexTest {
    @Test
    fun `should lex type identically to pattern`() {
        val contractGherkin = """
            Feature: Math API
            
            Scenario: Addition
              Given type Numbers (number*)
              When POST /add
              And request-body (Numbers)
              Then status 200
              And response-body (number)
        """.trimIndent()

        val contractBehaviour = ContractBehaviour(contractGherkin)

        val request = HttpRequest().updateMethod("POST").updatePath("/add").updateBody("[1, 2]")

        val response = contractBehaviour.lookup(request)

        assertEquals(200, response.status)

        try {
            (response.body ?: "hello").toInt()
        } catch (e: Exception) { fail("${response.body} is not a number")}
    }
}