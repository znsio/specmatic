package `in`.specmatic.core.pattern

import `in`.specmatic.core.parseGherkinString
import org.junit.jupiter.api.Test
import `in`.specmatic.shouldMatch

class StringMatchTest {
    @Test
    fun `string pattern should match null`() {
        val pattern = parsedPattern("""{"id": "(string?)"}""")
        val value = parsedValue("""{"id": null}""")

        value shouldMatch pattern
    }

    @Test
    fun `array of nulls should match json array pattern`() {
        val pattern = parsedPattern("""{"id": ["(string?...)"]}""")
        val value = parsedValue("""{"id": [null, null]}""")

        value shouldMatch pattern
    }

    @Test
    fun `null should match a string pattern value in a tabular pattern`() {
        val gherkin = """
            Feature: test
            
            Scenario: test
            Given pattern Person
            | id   | (number) |
            | name | (string?) |
        """.trimIndent()
        val pattern = rowsToTabularPattern(parseGherkinString(gherkin).feature.children[0].scenario.steps[0].dataTable.rows)
        val value = parsedValue("""{"id": 10, "name": null}""")

        value shouldMatch pattern
    }
}