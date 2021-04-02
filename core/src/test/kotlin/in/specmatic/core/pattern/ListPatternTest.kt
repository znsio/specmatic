package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.testBackwardCompatibilityInParallel
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.shouldNotMatch

internal class ListPatternTest {
    @Test
    fun `should generate a list of patterns each of which is a list pattern`() {
        val patterns = ListPattern(NumberPattern).newBasedOn(Row(), Resolver())

        for(pattern in patterns) {
            assertTrue(pattern is ListPattern)
        }
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch ListPattern(StringPattern)
    }

    @Test
    fun `should encompass itself`() {
        val type = ListPattern(NumberPattern)
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
        val bigger = ListPattern(AnyPattern(listOf(NumberPattern, NullPattern)))
        val smaller1Element = parsedPattern("""["(number)"]""")
        val smaller1ElementAndRest = parsedPattern("""["(number)", "(number...)"]""")

        assertThat(bigger.encompasses(smaller1Element, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smaller1ElementAndRest, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should fail if there are any match failures at all`() {
        val bigger = ListPattern(NumberPattern)
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
        val result = testBackwardCompatibilityInParallel(feature, feature)
        println(result.report())
        assertThat(result.success()).isTrue()
    }
}
