package run.qontract.core.pattern

import run.qontract.core.mustMatch
import run.qontract.core.parseGherkinString
import org.junit.jupiter.api.Test

class StringMatchTest {
    @Test
    fun `string pattern should match null`() {
        val pattern = parsedPattern("""{"id": "(string)"}""")
        val value = parsedValue("""{"id": null}""")

        value mustMatch pattern
    }

    @Test
    fun `array of nulls should match json array pattern`() {
        val pattern = parsedPattern("""{"id": ["(string*)"]}""")
        val value = parsedValue("""{"id": [null, null]}""")

        value mustMatch pattern
    }

    @Test
    fun `null should match a string pattern value in a tabular pattern`() {
        val gherkin = """
            Feature: test
            
            Scenario: test
            Given pattern Person
            | id   | (number) |
            | name | (string) |
        """.trimIndent()
        val pattern = rowsToTabularPattern(parseGherkinString(gherkin).feature.childrenList[0].scenario.stepsList[0].dataTable.rowsList)
        val value = parsedValue("""{"id": 10, "name": null}""")

        value mustMatch pattern
    }
}