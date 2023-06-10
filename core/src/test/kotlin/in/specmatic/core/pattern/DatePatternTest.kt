package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class DatePatternTest {
    @Test
    fun `should parse a valid date value`() {
        val dateString = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val dateValue = DatePattern.parse(dateString, Resolver())

        assertEquals(dateString, dateValue.string)
    }

    @Test
    fun `should generate a date value which can be parsed`() {
        val valueGenerated = DatePattern.generate(Resolver())
        val valueParsed = DatePattern.parse(valueGenerated.string, Resolver())

        assertEquals(valueGenerated, valueParsed)
    }

    @Test
    fun `should match a valid date value`() {
        val valueGenerated = DatePattern.generate(Resolver())
        valueGenerated shouldMatch DatePattern
    }

    @Test
    fun `should fail to match an invalid date value`() {
        val valueGenerated = StringValue("this is not a date value")
        valueGenerated shouldNotMatch DatePattern
    }

    @Test
    fun `should return itself when generating a new pattern based on a row`() {
        val datePatterns = DatePattern.newBasedOn(Row(), Resolver())
        assertEquals(1, datePatterns.size)
        assertEquals(DatePattern, datePatterns.first())
    }

    @Test
    fun `should match this date format`() {
        val date1 = StringValue("2020-04-12")
        val date2 = StringValue("2020-04-22")

        assertThat(DatePattern.matches(date1, Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(DatePattern.matches(date2, Resolver())).isInstanceOf(Result.Success::class.java)
    }
}
