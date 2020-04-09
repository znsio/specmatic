package run.qontract.core.pattern

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.StringValue
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
}