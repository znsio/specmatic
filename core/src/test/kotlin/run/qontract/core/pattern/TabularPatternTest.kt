package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch
import run.qontract.core.parseGherkinString
import org.junit.jupiter.api.Test
import run.qontract.core.value.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TabularPatternTest {
    @Test
    fun `A tabular pattern should match a JSON object value`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given pattern Id
| id   | (number) |
""".trim()

        val value = parsedValue("""{"id": 10}""")
        val pattern = rowsToTabularPattern(getRows(gherkin))

        value shouldMatch pattern
    }

    @Test
    fun `A tabular pattern can include a hardcoded number`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given pattern Id
| id   | 10 |
""".trim()

        val value = parsedValue("""{"id": 10}""")
        val pattern = rowsToTabularPattern(getRows(gherkin))

        value shouldMatch pattern
    }

    @Test
    fun `A number in a tabular pattern value will not match a string in a json object with the same key`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given pattern Id
| id   | "10" |
""".trim()

        val value = parsedValue("""{"id": 10}""")
        val pattern = rowsToTabularPattern(getRows(gherkin))

        value shouldNotMatch pattern
    }

    @Test
    fun `tabular pattern value can match boolean patterns and concrete values`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given pattern Status
| status1 | true     |
| status2 | (boolean) |
""".trim()

        val value = parsedValue("""{"status1": true, "status2": false}""")
        val pattern = rowsToTabularPattern(getRows(gherkin))

        value shouldMatch pattern
    }

    @Test
    fun `A concrete string can match strings`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given pattern Id
| id   | "12345" |
""".trim()

        val value = parsedValue("""{"id": "12345"}""")
        val pattern = rowsToTabularPattern(getRows(gherkin))

        value shouldMatch pattern
    }

    @Test
    fun `Repeating complex pattern should match an array with elements containing multiple primitive values of the specified type`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given pattern Ids
| ids | (Id*) |

Given pattern Id
| id   | (number) |
""".trim()

        val value = parsedValue("""{"ids": [{"id": 12345}, {"id": 12345}]}""")
        val scenario = getScenario(gherkin)

        val idsPattern = rowsToTabularPattern(scenario.stepsList[0].dataTable.rowsList)
        val idPattern = rowsToTabularPattern(scenario.stepsList[1].dataTable.rowsList)

        val resolver = Resolver(emptyMap(), false, mapOf("(Ids)" to idsPattern, "(Id)" to idPattern))

        assertTrue(idsPattern.matches(value, resolver).isTrue())
        assertTrue(resolver.matchesPattern(null, resolver.getPattern("(Ids)"), value).isTrue())
    }

    @Test
    fun `Repeating primitive pattern in table should match an array with elements containing multiple primitive values of the specified type`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given pattern Ids
| ids | (number*) |
""".trim()

        val value = parsedValue("""{"ids": [12345, 98765]}""")
        val scenario = getScenario(gherkin)

        val idsPattern = rowsToTabularPattern(scenario.stepsList[0].dataTable.rowsList)

        val resolver = Resolver(emptyMap(), false, mapOf("(Ids)" to idsPattern))

        assertTrue(idsPattern.matches(value, resolver).isTrue())
        assertTrue(resolver.matchesPattern(null, resolver.getPattern("(Ids)"), value).isTrue())
    }

    @Test
    fun `A tabular pattern should generate a new json object`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given pattern User
| id   | (number) |
| name | (string) |
""".trim()

        val value = rowsToTabularPattern(getRows(gherkin)).generate(Resolver())
        assertTrue(value.jsonObject["id"] is NumberValue)
        assertTrue(value.jsonObject["name"] is StringValue)
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

        val pattern = rowsToTabularPattern(getRows(gherkin))
        val newPattern = pattern.newBasedOn(Row(listOf("id"), listOf("10")), Resolver()).first()

        val value = newPattern.generate(Resolver())
        assertTrue(value is JSONObjectValue)
        value.jsonObject.getValue("id").let { assertEquals(10, (it as NumberValue).number) }
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
        val userPattern = rowsToTabularPattern(scenario.stepsList[0].dataTable.rowsList)
        val addressPattern = rowsToTabularPattern(scenario.stepsList[1].dataTable.rowsList)

        val row = Row(listOf("id", "flat"), listOf("10", "100"))

        val resolver = Resolver(patterns = mapOf("(User)" to userPattern, "(Address)" to addressPattern))

        val value = userPattern.newBasedOn(row, resolver).first().generate(resolver)

        assertTrue(value is JSONObjectValue)
        val id = value.jsonObject["id"] as NumberValue
        assertEquals(10, id.number)

        val address = value.jsonObject["address"]
        assertTrue(address is JSONObjectValue)
        address.jsonObject.getValue("flat").let { assertEquals(100, (it as NumberValue).number) }
    }

    @Test
    fun `tabular pattern can match null`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given request-body
| nothing | (null) |
""".trim()

        val scenario = getScenario(gherkin)

        val patternWithNullValue = rowsToTabularPattern(scenario.stepsList[0].dataTable.rowsList)
        val value = parsedValue("""{"nothing": null}""")
        value shouldMatch patternWithNullValue
    }

    @Test
    fun `tabular pattern can generate null`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given request-body
| nothing | (null) |
""".trim()

        val scenario = getScenario(gherkin)

        val patternWithNullValue = rowsToTabularPattern(scenario.stepsList[0].dataTable.rowsList)
        val value = patternWithNullValue.generate(Resolver())

        assertTrue(value.jsonObject.getValue("nothing") is NullValue)
    }

    @Test
    fun `tabular pattern can pick up null values from examples`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given request-body
| nothing | (string?) |
""".trim()

        val scenario = getScenario(gherkin)

        val patternWithNullValue = rowsToTabularPattern(scenario.stepsList[0].dataTable.rowsList)
        val example = Row(listOf("nothing"), listOf("(null)"))
        val newPatterns = patternWithNullValue.newBasedOn(example, Resolver())

        assertEquals(1, newPatterns.size)

        val value = newPatterns[0].generate(Resolver())

        if(value !is JSONObjectValue) fail("Expected JSON object")

        assertTrue(value.jsonObject.getValue("nothing") is NullValue)
    }

    @Test
    fun `tabular pattern should pick up null values from examples but fail with non nullable pattern`() {
        val gherkin = """
Feature: test feature

Scenario: api call
Given request-body
| nothing | (string) |
""".trim()

        val scenario = getScenario(gherkin)

        val patternWithNullValue = rowsToTabularPattern(scenario.stepsList[0].dataTable.rowsList)
        val example = Row(listOf("nothing"), listOf("(null)"))
        val newPatterns = patternWithNullValue.newBasedOn(example, Resolver())

        assertEquals(1, newPatterns.size)

        val value = newPatterns[0].generate(Resolver())
        if(value !is JSONObjectValue) fail("Expected JSON object")

        val nothingValue = value.jsonObject.getValue("nothing")
        if(nothingValue !is StringValue) fail("Expected string")

        assertEquals("(null)", nothingValue.string)
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch TabularPattern(mapOf("name" to StringPattern()))
    }
}

internal fun getRows(gherkin: String) = getScenario(gherkin).stepsList[0].dataTable.rowsList

internal fun getScenario(gherkin: String) = parseGherkinString(gherkin).feature.childrenList[0].scenario
