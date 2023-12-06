package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch

internal class DateTimePatternTest {
    @Test
    fun `should parse a valid datetime value`() {
        val dateString = currentDateTimeInRFC339Format().string
        val dateValue = DateTimePattern.parse(dateString, Resolver())

        assertEquals(dateString, dateValue.string)
    }

    @Test
    fun `should generate a datetime value which can be parsed`() {
        val valueGenerated = DateTimePattern.generate(Resolver())
        val valueParsed = DateTimePattern.parse(valueGenerated.string, Resolver())

        assertEquals(valueGenerated, valueParsed)
    }

    @Test
    fun `should match a valid datetime value`() {
        val valueGenerated = DateTimePattern.generate(Resolver())
        valueGenerated shouldMatch DateTimePattern
    }

    @Test
    fun `should fail to match an invalid datetime value`() {
        val valueGenerated = StringValue("this is not a datetime value")
        valueGenerated shouldNotMatch DateTimePattern
    }

    @Test
    fun `should return itself when generating a new pattern based on a row`() {
        val datePatterns = DateTimePattern.newBasedOn(Row(), Resolver())
        assertEquals(1, datePatterns.size)
        assertEquals(DateTimePattern, datePatterns.first())
    }

    @Test
    fun `should match RFC3339 date time format`() {
        val date1 = StringValue("2020-04-12T00:00:00+05:30")
        val date2 = StringValue("2014-12-03T10:05:59+08:00")

        assertThat(DateTimePattern.matches(date1, Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(DateTimePattern.matches(date2, Resolver())).isInstanceOf(Result.Success::class.java)
    }
}