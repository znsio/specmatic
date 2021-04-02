package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import `in`.specmatic.core.Resolver
import org.junit.jupiter.api.Test
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.Result
import `in`.specmatic.core.testBackwardCompatibilityInParallel
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch
import kotlin.test.assertFalse

internal class JSONArrayPatternTest {
    @Test
    fun `An empty array should match an array matcher`() {
        val value = parsedValue("[]")
        val pattern = parsedPattern("""["(number*)"]""")

        value shouldMatch pattern
    }

    @Test
    fun `An array with the first n elements should not match an array with all the elements`() {
        val value = parsedValue("[1,2]")
        val pattern = parsedPattern("""[1,2,3]""")

        assertFalse(pattern.matches(value, Resolver()).isTrue())

    }

    @Test
    fun `should match the rest even if there are no more elements`() {
        val pattern = JSONArrayPattern(listOf(StringPattern, RestPattern(NumberPattern)))
        val value = JSONArrayValue(listOf(StringValue("hello")))

        value shouldMatch pattern
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch JSONArrayPattern(listOf(StringPattern, StringPattern))
    }

    @Test
    fun `should encompass itself`() {
        val type = parsedPattern("""["(number)", "(number)"]""")
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should not encompass a pattern with a different number of items`() {
        val bigger = parsedPattern("""["(number)", "(number)"]""")
        val smallerLess = parsedPattern("""["(number)"]""")
        val smallerMore = parsedPattern("""["(number)"]""")

        assertThat(bigger.encompasses(smallerLess, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
        assertThat(bigger.encompasses(smallerMore, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `finite array should not encompass a list`() {
        val smaller = parsedPattern("""["(number)", "(number)"]""")
        val bigger = ListPattern(NumberPattern)

        assertThat(smaller.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should encompass a list containing a subtype of all elements`() {
        val bigger = parsedPattern("""["(number)", "(number...)"]""")
        val alsoBigger = parsedPattern("""["(number...)"]""")
        val matching = ListPattern(NumberPattern)

        assertThat(bigger.encompasses(matching, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(alsoBigger.encompasses(matching, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `finite array should not encompass an infinite array`() {
        val bigger = parsedPattern("""["(number)", "(number...)"]""")
        val smaller = parsedPattern("""["(number)", "(number)", "(number)"]""")

        assertThat(smaller.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `smaller infinite array should match larger infinite array if all types match`() {
        val bigger = parsedPattern("""["(number)", "(number...)"]""")
        val matching = parsedPattern("""["(number)", "(number)", "(number...)"]""")

        assertThat(bigger.encompasses(matching, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `bigger infinite array should match smaller infinite array if all types match`() {
        val bigger = parsedPattern("""["(number)", "(number)", "(number...)"]""")
        val matching = parsedPattern("""["(number)", "(number...)"]""")

        assertThat(bigger.encompasses(matching, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should fail if there are any match failures at all`() {
        val bigger = parsedPattern("""["(number)", "(number...)"]""")
        val matching = parsedPattern("""["(number)", "(string...)"]""")

        assertThat(bigger.encompasses(matching, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `json array type with recursive type definition should be validated without an infinite loop`() {
        val gherkin = """
Feature: Recursive test

  Scenario: Recursive scenario
    Given type Data ["(number)", "(Data)"]
    When GET /
    Then status 200
    And response-body (Data)
""".trim()

        val feature = parseGherkinStringToFeature(gherkin)
        val result = testBackwardCompatibilityInParallel(feature, feature)
        println(result.report())
        assertThat(result.success()).isTrue()
    }

    @Test
    fun `json array of objects with recursive type definition should be validated without an infinite loop`() {
        val gherkin = """
Feature: Recursive test

  Scenario: Recursive scenario
    Given type Data ["(number)", "(MoreData)"]
    And type MoreData
    | data | (Data) |
    When GET /
    Then status 200
    And response-body (Data)
""".trim()

        val feature = parseGherkinStringToFeature(gherkin)
        val result = testBackwardCompatibilityInParallel(feature, feature)
        println(result.report())
        assertThat(result.success()).isTrue()
    }

    @Test
    fun `json array of object containing list with recursive type definition should be validated without an infinite loop`() {
        val gherkin = """
Feature: Recursive test

  Scenario: Recursive scenario
    Given type Data ["(number)", "(MoreData)"]
    And type MoreData
    | data | (Data*) |
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
