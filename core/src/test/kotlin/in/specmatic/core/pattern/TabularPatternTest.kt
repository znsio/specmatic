package `in`.specmatic.core.pattern

import `in`.specmatic.core.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch
import `in`.specmatic.stub.HttpStub
import com.fasterxml.jackson.annotation.JsonProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.springframework.web.client.RestTemplate
import java.net.URI

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

        val idsPattern = rowsToTabularPattern(scenario.steps[0].dataTable.rows)
        val idPattern = rowsToTabularPattern(scenario.steps[1].dataTable.rows)

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

        val idsPattern = rowsToTabularPattern(scenario.steps[0].dataTable.rows)

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
        if (value !is JSONObjectValue)
            fail("Expected $value to be JSON")
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
        val userPattern = rowsToTabularPattern(scenario.steps[0].dataTable.rows)
        val addressPattern = rowsToTabularPattern(scenario.steps[1].dataTable.rows)

        val row = Row(listOf("id", "flat"), listOf("10", "100"))

        val resolver = Resolver(newPatterns = mapOf("(User)" to userPattern, "(Address)" to addressPattern))

        val value = userPattern.newBasedOn(row, resolver).first().generate(resolver)
        if (value !is JSONObjectValue)
            fail("Expected $value to be JSON")
        val id = value.jsonObject["id"] as NumberValue
        assertEquals(10, id.number)

        val address = value.jsonObject["address"]
        if (address !is JSONObjectValue)
            fail("Expected $address to be JSON")
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

        val patternWithNullValue = rowsToTabularPattern(scenario.steps[0].dataTable.rows)
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

        val patternWithNullValue = rowsToTabularPattern(scenario.steps[0].dataTable.rows)
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

        val patternWithNullValue = rowsToTabularPattern(scenario.steps[0].dataTable.rows)
        val example = Row(listOf("nothing"), listOf("(null)"))
        val newPatterns = patternWithNullValue.newBasedOn(example, Resolver())

        assertEquals(1, newPatterns.size)

        val value = newPatterns[0].generate(Resolver())

        if (value !is JSONObjectValue) fail("Expected JSON object")

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

        val patternWithNullValue = rowsToTabularPattern(scenario.steps[0].dataTable.rows)
        val example = Row(listOf("nothing"), listOf("(null)"))
        assertThrows<ContractException> { patternWithNullValue.newBasedOn(example, Resolver()) }
    }

    @Test
    fun `error message when a json object does not match nullable primitive such as string in the contract`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: test feature
  Scenario: api call
    Given type Request
    | name | (string?) |
    When POST /
    And request-body (Request)
    Then status 200
""".trim()
        )

        val request =
            HttpRequest("POST", "/", body = parsedValue("""{"name": {"firstname": "Jane", "lastname": "Doe"}}"""))

        val resolver = feature.scenarios.single().resolver
        val result = feature.scenarios.single().httpRequestPattern.matches(request, resolver)

        assertThat(resultReport(result)).isEqualTo(
            """>> REQUEST.BODY.name

Expected string, actual was json object: {
    "firstname": "Jane",
    "lastname": "Doe"
}"""
        )
    }

    @Test
    fun `tabular type with recursive type definition should be validated without an infinite loop`() {
        val gherkin = """
Feature: Recursive test

  Scenario: Recursive scenario
    Given type Data
    | id   | (number) |
    | data | (Data)   |
    When GET /
    Then status 200
    And response-body (Data)
""".trim()

        val feature = parseGherkinStringToFeature(gherkin)
        val result = testBackwardCompatibility(feature, feature)
        assertThat(result.success()).isTrue()
    }

    //TODO:
    @Ignore
    fun `tabular type with recursive type definition should generate response with infinite loop`() {
        val gherkin = """
Feature: Recursive test

  Scenario: Recursive scenario
    Given type Data
    | id   | (number) |
    | data | (Data)   |
    When GET /
    Then status 200
    And response-body (Data)
""".trim()

        val feature = parseGherkinStringToFeature(gherkin)

        val response = HttpStub(feature).use {
            val restTemplate = RestTemplate()
            restTemplate.getForObject(
                URI.create("http://localhost:9000/"),
                Data::class.java
            )
        }

        assertThat(response).isNotNull
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch toTabularPattern(mapOf("name" to StringPattern()))
    }

    @Test
    fun `tabular type should encompass JSON type`() {
        val tabular = toTabularPattern(mapOf("key1" to StringPattern(), "key2" to StringPattern()))
        val json = JSONObjectPattern(tabular.pattern)

        assertThat(tabular.encompasses(json, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `structure with fewer keys encompasses one with same keys plus more`() {
        val smaller = toTabularPattern(mapOf("key1" to StringPattern(), "key2" to StringPattern()))
        val bigger = toTabularPattern(mapOf("key1" to StringPattern()))

        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `structure does not encompass one with missing keys`() {
        val smaller = toTabularPattern(mapOf("key1" to StringPattern(), "key2" to StringPattern()))
        val bigger = toTabularPattern(mapOf("key1" to StringPattern()))

        assertThat(smaller.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `it should encompass itself`() {
        val type = toTabularPattern(mapOf("number" to NumberPattern()))
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass itself with a nullable value`() {
        val type = toTabularPattern(mapOf("number" to parsedPattern("(number?)")))
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `having a nullable value it should encompass another with a non null value of the same type`() {
        val bigger = toTabularPattern(mapOf("number" to parsedPattern("(number?)")))
        val smallerWithNumber = toTabularPattern(mapOf("number" to NumberPattern()))
        val smallerWithNull = toTabularPattern(mapOf("number" to NullPattern))

        assertThat(
            bigger.encompasses(
                smallerWithNumber,
                Resolver(),
                Resolver()
            )
        ).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smallerWithNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass with an optional key`() {
        val type = toTabularPattern(mapOf("number?" to parsedPattern("(number)")))
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass another with the optional key missing`() {
        val bigger = toTabularPattern(mapOf("required" to NumberPattern(), "optional?" to NumberPattern()))
        val smaller = toTabularPattern(mapOf("required" to NumberPattern()))
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass another with an unexpected key`() {
        val bigger = toTabularPattern(mapOf("required" to NumberPattern()))
        val smaller = toTabularPattern(mapOf("required" to NumberPattern(), "extra" to NumberPattern()))
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass itself when ellipsis is present`() {
        val bigger = toTabularPattern(mapOf<String, Pattern>("data" to NumberPattern(), "..." to StringPattern()))
        assertThat(bigger.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `type with ellipsis is equivalent to a type with the same keys except the ellipsis`() {
        val theOne = toTabularPattern(mapOf<String, Pattern>("data" to NumberPattern()))
        val theOther = toTabularPattern(mapOf<String, Pattern>("data" to NumberPattern(), "..." to StringPattern()))

        assertThat(theOne.encompasses(theOther, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(theOther.encompasses(theOne, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }
}

internal fun getRows(gherkin: String) = getScenario(gherkin).steps[0].dataTable.rows

internal fun getScenario(gherkin: String) = parseGherkinString(gherkin)!!.feature.children[0].scenario

data class Data(
    @JsonProperty("id") val name: Int,
    @JsonProperty("data") val data: Data
)