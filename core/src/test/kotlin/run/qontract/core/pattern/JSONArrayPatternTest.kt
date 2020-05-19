package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import run.qontract.core.Resolver
import run.qontract.core.shouldMatch
import org.junit.jupiter.api.Test
import run.qontract.core.Result
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
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
        val pattern = JSONArrayPattern(listOf(StringPattern, RestPattern(NumberTypePattern)))
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
        assertThat(type.encompasses2(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should not encompass a pattern with a different number of items`() {
        val bigger = parsedPattern("""["(number)", "(number)"]""")
        val smallerLess = parsedPattern("""["(number)"]""")
        val smallerMore = parsedPattern("""["(number)"]""")

        assertThat(bigger.encompasses2(smallerLess, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
        assertThat(bigger.encompasses2(smallerMore, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `finite array should not encompass a list`() {
        val smaller = parsedPattern("""["(number)", "(number)"]""")
        val bigger = ListPattern(NumberTypePattern)

        assertThat(smaller.encompasses2(bigger, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should encompass a list containing a subtype of all elements`() {
        val bigger = parsedPattern("""["(number)", "(number...)"]""")
        val alsoBigger = parsedPattern("""["(number...)"]""")
        val matching = ListPattern(NumberTypePattern)

        assertThat(bigger.encompasses2(matching, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(alsoBigger.encompasses2(matching, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `finite array should not encompass an infinite array`() {
        val bigger = parsedPattern("""["(number)", "(number...)"]""")
        val smaller = parsedPattern("""["(number)", "(number)", "(number)"]""")

        assertThat(smaller.encompasses2(bigger, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `smaller infinite array should match larger infinite array if all types match`() {
        val bigger = parsedPattern("""["(number)", "(number...)"]""")
        val matching = parsedPattern("""["(number)", "(number)", "(number...)"]""")

        assertThat(bigger.encompasses2(matching, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `bigger infinite array should match smaller infinite array if all types match`() {
        val bigger = parsedPattern("""["(number)", "(number)", "(number...)"]""")
        val matching = parsedPattern("""["(number)", "(number...)"]""")

        assertThat(bigger.encompasses2(matching, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should fail if there are any match failures at all`() {
        val bigger = parsedPattern("""["(number)", "(number...)"]""")
        val matching = parsedPattern("""["(number)", "(string...)"]""")

        assertThat(bigger.encompasses2(matching, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }
}
