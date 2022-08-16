package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.NumberValue

internal class RepeatingPatternTest {
    @Test
    fun `Repeating primitive pattern should match an array with elements containing multiple primitive values of the specified type`() {
        val value = parsedValue("""[12345, 98765]""")
        val pattern = parsedPattern("(number*)")

        pattern.matches(value, Resolver())
    }

    @Test
    fun `Repeating complex pattern in an array should match an array of matching json objects`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given pattern Id
| id | (number) |
""".trim()

        val value = parsedValue("""[{"id": 12345}, {"id": 98765}]""")
        val idPattern = rowsToTabularPattern(getRows(gherkin))
        val resolver = Resolver(newPatterns = mapOf("(Id)" to idPattern))

        val idArrayPattern = parsedPattern("(Id*)")
        assertTrue(idArrayPattern.matches(value, resolver).isSuccess())
    }

    @Test
    fun `Array spec should generate a list of random length`() {
        val numbersPattern = parsedPattern("(number*)")
        val jsonArray = numbersPattern.generate(Resolver())

        assertTrue(jsonArray is JSONArrayValue)
        if(jsonArray is JSONArrayValue)
        {
            for(value in jsonArray.list) {
                assertTrue(value is NumberValue)
            }
        }
    }
}
