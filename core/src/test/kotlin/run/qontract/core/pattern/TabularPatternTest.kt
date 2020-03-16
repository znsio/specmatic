package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.mustMatch
import run.qontract.core.mustNotMatch
import run.qontract.core.parseGherkinString
import run.qontract.core.value.JSONObjectValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabularPatternTest {
    @Test
    fun `A tabular pattern should match a JSON object value`() {
        val gherkin = """
Feature: test feature

Scenario:
Given pattern Id
| id   | (number) |
""".trim()

        val value = parsedValue("""{"id": 10}""")
        val pattern = rowsToPattern(getRows(gherkin))

        value mustMatch pattern
    }

    @Test
    fun `A tabular pattern can include a hardcoded number`() {
        val gherkin = """
Feature: test feature

Scenario:
Given pattern Id
| id   | 10 |
""".trim()

        val value = parsedValue("""{"id": 10}""")
        val pattern = rowsToPattern(getRows(gherkin))

        value mustMatch pattern
    }

    @Test
    fun `A number in a tabular pattern value will not match a string in a json object with the same key`() {
        val gherkin = """
Feature: test feature

Scenario:
Given pattern Id
| id   | "10" |
""".trim()

        val value = parsedValue("""{"id": 10}""")
        val pattern = rowsToPattern(getRows(gherkin))

        value mustNotMatch pattern
    }

    @Test
    fun `A number in a tabular pattern value can match boolean patterns and concrete values`() {
        val gherkin = """
Feature: test feature

Scenario:
Given pattern Status
| status1 | true     |
| status2 | (boolean) |
""".trim()

        val value = parsedValue("""{"status1": true, "status2": false}""")
        val pattern = rowsToPattern(getRows(gherkin))

        value mustMatch pattern
    }

    @Test
    fun `A number in a tabular pattern value can match strings`() {
        val gherkin = """
Feature: test feature

Scenario:
Given pattern Id
| id   | "12345" |
""".trim()

        val value = parsedValue("""{"id": "12345"}""")
        val pattern = rowsToPattern(getRows(gherkin))

        value mustMatch pattern
    }

    @Test
    fun `Repeating complex pattern should match an array with elements containing multiple primitive values of the specified type`() {
        val gherkin = """
Feature: test feature

Scenario:
Given pattern Ids
| ids | (Id*) |

Given pattern Id
| id   | (number) |
""".trim()

        val value = parsedValue("""{"ids": [{"id": 12345}, {"id": 12345}]}""")
        val scenario = getScenario(gherkin)

        val idsPattern = rowsToPattern(scenario.stepsList[0].dataTable.rowsList)
        val idPattern = rowsToPattern(scenario.stepsList[1].dataTable.rowsList)

        val resolver = Resolver(HashMap(), false)
        resolver.addCustomPattern("(Ids)", idsPattern)
        resolver.addCustomPattern("(Id)", idPattern)

        assertTrue(idsPattern.matches(value, resolver).toBoolean())
        assertTrue(resolver.matchesPattern(null, "(Ids)", value).toBoolean())
    }

    @Test
    fun `Repeating primitive pattern in table should match an array with elements containing multiple primitive values of the specified type`() {
        val gherkin = """
Feature: test feature

Scenario:
Given pattern Ids
| ids | (number*) |
""".trim()

        val value = parsedValue("""{"ids": [12345, 98765]}""")
        val scenario = getScenario(gherkin)

        val idsPattern = rowsToPattern(scenario.stepsList[0].dataTable.rowsList)

        val resolver = Resolver(HashMap(), false)
        resolver.addCustomPattern("(Ids)", idsPattern)

        assertTrue(idsPattern.matches(value, resolver).toBoolean())
        assertTrue(resolver.matchesPattern(null, "(Ids)", value).toBoolean())
    }

    @Test
    fun `A tabular pattern should generate a new json object`() {
        val gherkin = """
Feature: test feature

Scenario:
Given pattern User
| id   | (number) |
| name | (string) |
""".trim()

        val value = rowsToPattern(getRows(gherkin)).generate(Resolver())
        assertTrue(value is JSONObjectValue)
        assertTrue(value.jsonObject["id"] is Number)
        assertTrue(value.jsonObject["name"] is String)
    }

    @Test
    fun `A tabular pattern should replace a key with a value in examples`() {
        val gherkin = """
Feature: test feature

Scenario Outline:
Given pattern User
| id   | (number) |
| name | (string) |
""".trim()

        val pattern = rowsToPattern(getRows(gherkin))
        val newPattern = pattern.newBasedOn(Row(listOf("id"), listOf("10")), Resolver())

        val value = newPattern.generate(Resolver())
        assertTrue(value is JSONObjectValue)
        assertEquals(10, value.jsonObject["id"])
    }

    @Test
    fun `A nested tabular pattern should replace a key with a value in examples`() {
        val gherkin = """
Feature: test feature

Scenario Outline:
Given pattern User
| id      | (number)  |
| name    | (string)  |
| address | (Address) |

And pattern Address
| flat   | (number) |
| bldg   | (string) |
""".trim()

        val scenario = getScenario(gherkin)
        val userPattern = rowsToPattern(scenario.stepsList[0].dataTable.rowsList)
        val addressPattern = rowsToPattern(scenario.stepsList[1].dataTable.rowsList)

        val row = Row(listOf("id", "flat"), listOf("10", "100"))

        val resolver = Resolver()
        resolver.addCustomPattern("(User)", userPattern)
        resolver.addCustomPattern("(Address)", addressPattern)

        val value = userPattern.newBasedOn(row, resolver).generate(resolver)

        assertTrue(value is JSONObjectValue)
        assertEquals(10, value.jsonObject["id"])

        val address = value.jsonObject["address"]
        assertEquals(100, (address as Map<String, Any>)["flat"])
    }
}

internal fun getRows(gherkin: String) = getScenario(gherkin).stepsList[0].dataTable.rowsList

internal fun getScenario(gherkin: String) = parseGherkinString(gherkin).feature.childrenList[0].scenario
