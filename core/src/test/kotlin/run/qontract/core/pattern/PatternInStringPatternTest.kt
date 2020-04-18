package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import run.qontract.core.Resolver
import run.qontract.core.shouldMatch
import run.qontract.core.value.StringValue

internal class PatternInStringPatternTest {
    @Test
    fun `should match a number in string`() {
        StringValue("10") shouldMatch PatternInStringPattern(NumberTypePattern)
    }

    @Test
    fun `should match a boolean in string`() {
        StringValue("true") shouldMatch PatternInStringPattern(BooleanPattern)
    }

    @Test
    fun `should generate a number in a string`() {
        val value = PatternInStringPattern(NumberTypePattern).generate(Resolver())

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
        val patterns = PatternInStringPattern(NumberTypePattern).newBasedOn(Row(), Resolver())

        assertThat(patterns).hasSize(1)

        val pattern = patterns.first()

        assertThat(pattern).isInstanceOf(PatternInStringPattern::class.java)

        if(pattern !is PatternInStringPattern) fail("Expected PatternInStringPattern")

        assertThat(pattern.pattern).isInstanceOf(NumberTypePattern::class.java)
    }

    @Test
    fun `should parse a string`() {
        val pattern = PatternInStringPattern(NumberTypePattern)
        val value = pattern.parse("10", Resolver())

        assertThat(value).isEqualTo(StringValue("10"))
    }

    @Test
    fun `should match another pattern-in-string of the same inner pattern type`() {
        val pattern1 = PatternInStringPattern(NumberTypePattern)
        val pattern2 = PatternInStringPattern(NumberTypePattern)

        assertThat(pattern1.matchesPattern(pattern2, Resolver())).isTrue()

        val pattern3 = PatternInStringPattern(BooleanPattern)
        assertThat(pattern1.matchesPattern(pattern3, Resolver())).isFalse()
    }
}
