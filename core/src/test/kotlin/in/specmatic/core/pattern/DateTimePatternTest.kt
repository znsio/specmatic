package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DateTimePatternTest {
    @Test
    fun `should parse a valid datetime value`() {
        val dateString = RFC3339.currentDateTime()
        val dateValue = DateTimePattern.parse(dateString, Resolver())

        assertThat(dateValue.string).isEqualTo(dateString)
    }

    @Test
    fun `should generate a datetime value which can be parsed`() {
        val valueGenerated = DateTimePattern.generate(Resolver())
        val valueParsed = DateTimePattern.parse(valueGenerated.string, Resolver())

        assertThat(valueParsed).isEqualTo(valueGenerated)
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
        assertThat(datePatterns.size).isEqualTo(1)
        assertThat(datePatterns.first()).isEqualTo(DateTimePattern)
    }

    @Test
    fun `should match RFC3339 date time format`() {
        val date1 = StringValue("2020-04-12T00:00:00+05:30")
        val date2 = StringValue("2014-12-03T10:05:59+08:00")

        assertThat(DateTimePattern.matches(date1, Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(DateTimePattern.matches(date2, Resolver())).isInstanceOf(Result.Success::class.java)
    }
}