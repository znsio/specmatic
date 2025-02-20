package io.specmatic.core.pattern

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TimePatternTest {
    @Test
    fun testValidTimeMatch() {
        val timeString = "10:05:59"
        val result = TimePattern.matches(StringValue(timeString), Resolver())
        assertTrue(result is Result.Success)
    }

    @Test
    fun testInvalidTimeMatch() {
        val timeString = "invalid-time"
        val result = TimePattern.matches(StringValue(timeString), Resolver())
        assertTrue(result is Result.Failure)
    }

    @Test
    fun testGeneratedTimeIsValid() {
        val generated = TimePattern.generate(Resolver())
        println(generated)
        val result = TimePattern.matches(generated, Resolver())
        assertTrue(result is Result.Success)
    }

    @Test
    fun testParseValidTime() {
        val parsed = TimePattern.parse("23:59:59", Resolver())
        assertEquals("23:59:59", parsed.string)
    }

    @Test
    fun testNewBasedOn() {
        val row = Row()
        val patterns = TimePattern.newBasedOn(row, Resolver()).map { it.value }.toList()

        assertEquals(1, patterns.size)
        assertEquals(TimePattern, patterns.first())

        assertThat(patterns).allSatisfy {
            val time = it.generate(Resolver())
            val match = TimePattern.matches(time, Resolver())
            assertThat(match).isInstanceOf(Result.Success::class.java)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "01:01:01, true",
        "a23:59:59, false",
        "235959, true",
        "01:01:01Z, true",
        "010101Z, true",
        "01:01:01+05:30, true",
        "01:01:01+05:30d, false",
        "010101+0530, true",
        "010101d+0530, false",
        "010101+0530d, false",
        "01:01:01-01:00, true",
        "01:01:01b-01:00a, false",
        "010101-0100, true",
        "010101c-0100d, false",
    )
    fun `RFC 6801 regex should validate time`(time: String, isValid: Boolean) {
        val result = TimePattern.matches(StringValue(time), Resolver())
        assertEquals(isValid, result is Result.Success)
    }
}