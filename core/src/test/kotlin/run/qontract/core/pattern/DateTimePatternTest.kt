package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.StringValue
import run.qontract.shouldMatch
import run.qontract.shouldNotMatch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class DateTimePatternTest {
    @Test
    fun `should parse a valid datetime value`() {
        val dateString = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
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
    fun `should match this date time format`() {
        val date1 = StringValue("2020-04-12T00:00:00")
        val date2 = StringValue("2020-04-22T23:59:59")

        assertThat(DateTimePattern.matches(date1, Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(DateTimePattern.matches(date2, Resolver())).isInstanceOf(Result.Success::class.java)
    }
}