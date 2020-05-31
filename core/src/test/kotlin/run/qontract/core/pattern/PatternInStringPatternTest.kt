package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.shouldMatch
import run.qontract.core.value.StringValue

internal class PatternInStringPatternTest {
    @Test
    fun `should match a number in string`() {
        StringValue("10") shouldMatch PatternInStringPattern(NumberPattern)
    }

    @Test
    fun `should match a boolean in string`() {
        StringValue("true") shouldMatch PatternInStringPattern(BooleanPattern)
    }

    @Test
    fun `should generate a number in a string`() {
        val value = PatternInStringPattern(NumberPattern).generate(Resolver())

        assertThat(value).isInstanceOf(StringValue::class.java)
        if(value !is StringValue)
            fail("Expected StringValue")

        try {
            value.string.toInt()
        } catch(e: Throwable) {
            fail("Value was not a number: ${e.message}")
        }
    }

    @Test
    fun `should generate a list of patterns based on a Row`() {
        val patterns = PatternInStringPattern(NumberPattern).newBasedOn(Row(), Resolver())

        assertThat(patterns).hasSize(1)

        val pattern = patterns.first()

        assertThat(pattern).isInstanceOf(PatternInStringPattern::class.java)

        if(pattern !is PatternInStringPattern) fail("Expected PatternInStringPattern")

        assertThat(pattern.pattern).isInstanceOf(NumberPattern::class.java)
    }

    @Test
    fun `should parse a string`() {
        val pattern = PatternInStringPattern(NumberPattern)
        val value = pattern.parse("10", Resolver())

        assertThat(value).isEqualTo(StringValue("10"))
    }

    @Test
    fun `should match another pattern-in-string of the same inner pattern type`() {
        val pattern1 = PatternInStringPattern(NumberPattern)
        val pattern2 = PatternInStringPattern(NumberPattern)

        assertThat(pattern1.encompasses(pattern2, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)

        val pattern3 = PatternInStringPattern(BooleanPattern)
        assertThat(pattern1.encompasses(pattern3, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should encompass itself`() {
        val type = parsedPattern("""(number in string)""")
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should not encompass another contained type`() {
        val numberInString = parsedPattern("""(number in string)""")
        val booleanInString = parsedPattern("""(boolean in string)""")
        assertThat(numberInString.encompasses(booleanInString, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }
}
