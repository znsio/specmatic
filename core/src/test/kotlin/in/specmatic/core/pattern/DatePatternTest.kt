package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class DatePatternTest {
    @Test
    fun `should parse a valid date value`() {
        val dateString = LocalDate.now().format(RFC3339.dateFormatter)
        val dateValue = DatePattern.parse(dateString, Resolver())

        assertThat(dateString).isEqualTo(dateValue.string)
    }

    @Test
    fun `should generate a date value which can be parsed`() {
        val valueGenerated = DatePattern.generate(Resolver())
        val valueParsed = DatePattern.parse(valueGenerated.string, Resolver())

        assertThat(valueGenerated).isEqualTo(valueParsed)
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
        assertThat(1).isEqualTo(datePatterns.size)
        assertThat(DatePattern).isEqualTo(datePatterns.first())
    }

    @Test
    fun `should match RFC3339 date format`() {
        val date1 = StringValue("2020-04-12")
        val date2 = StringValue("2020-04-22")

        assertThat(DatePattern.matches(date1, Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(DatePattern.matches(date2, Resolver())).isInstanceOf(Result.Success::class.java)
    }
}
