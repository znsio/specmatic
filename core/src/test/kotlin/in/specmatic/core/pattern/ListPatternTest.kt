package `in`.specmatic.core.pattern

import `in`.specmatic.GENERATIVE
import `in`.specmatic.core.*
import `in`.specmatic.core.value.JSONArrayValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.shouldNotMatch
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag

internal class ListPatternTest {
    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch ListPattern(StringPattern())
    }

    @Test
    fun `should encompass itself`() {
        val type = ListPattern(NumberPattern())
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `list of nullable type should encompass another list the same non-nullable type`() {
        val bigger = ListPattern(parsedPattern("""(number?)"""))
        val smallerWithNumber = ListPattern(parsedPattern("""(number)"""))
        val smallerWithNull = ListPattern(parsedPattern("""(number)"""))
        assertThat(bigger.encompasses(smallerWithNumber, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smallerWithNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should not encompass another list with different type`() {
        val numberPattern = ListPattern(parsedPattern("""(number?)"""))
        val stringPattern = ListPattern(parsedPattern("""(string)"""))
        assertThat(numberPattern.encompasses(stringPattern, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `a list should encompass a json array with items matching the list`() {
        val bigger = ListPattern(AnyPattern(listOf(NumberPattern(), NullPattern)))
        val smaller1Element = parsedPattern("""["(number)"]""")
        val smaller1ElementAndRest = parsedPattern("""["(number)", "(number...)"]""")

        assertThat(bigger.encompasses(smaller1Element, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smaller1ElementAndRest, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should fail if there are any match failures at all`() {
        val bigger = ListPattern(NumberPattern())
        val matching = parsedPattern("""["(number)", "(string...)"]""")

        assertThat(bigger.encompasses(matching, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `list type with recursive type definition should be validated without an infinite loop`() {
        val gherkin = """
Feature: Recursive test

  Scenario: Recursive scenario
    Given type Data
    | id   | (number) |
    | data | (Data*)   |
    When GET /
    Then status 200
    And response-body (Data)
""".trim()

        val feature = parseGherkinStringToFeature(gherkin)
        val result = testBackwardCompatibility(feature, feature)
        println(result.report())
        assertThat(result.success()).isTrue()
    }

    @Nested
    inner class ReturnAllErrors {
        val listType = ListPattern(NumberPattern())
        val list = parsedJSON("""["elementA", 2, "elementC"]""")
        val result: Result.Failure = listType.matches(list, Resolver()) as Result.Failure
        val resultText = result.toFailureReport().toText()

        @Test
        fun `should return all errors in a list`() {
            assertThat(result.toMatchFailureDetailList()).hasSize(2)
        }

        @Test
        fun `should refer to all errors in the report`() {
            println(resultText)
            assertThat(resultText).contains("[0]")
            assertThat(resultText).contains("elementA")
            assertThat(resultText).contains("[2]")
            assertThat(resultText).contains("elementC")
        }
    }

    @Tag(GENERATIVE)
    @Test
    fun `negative pattern generation`() {
        val negativePatterns = ListPattern(StringPattern()).negativeBasedOn(Row(), Resolver())
        assertThat(negativePatterns.map { it.typeName }).containsExactlyInAnyOrder(
            "null"
        )
    }

    @Tag(GENERATIVE)
    @Test
    fun `should generate a list of patterns each of which is a list pattern`() {
        val patterns = ListPattern(NumberPattern()).newBasedOn(Row(), Resolver())

        for(pattern in patterns) {
            assertTrue(pattern is ListPattern)
        }
    }

    @Tag(GENERATIVE)
    @Test
    fun `should use the inline example for generation of values`() {
        try {
            System.setProperty(Flags.schemaExampleDefault, "true")

            val value = ListPattern(NumberPattern(), example = listOf("1", "2", "3")).generate(Resolver())
            assertThat(value).isEqualTo(JSONArrayValue(listOf(NumberValue(1), NumberValue(2), NumberValue(3))))
        } finally {
            System.clearProperty(Flags.schemaExampleDefault)
        }
    }
}
