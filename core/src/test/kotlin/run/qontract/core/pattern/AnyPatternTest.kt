package run.qontract.core.pattern

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.shouldMatch
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import kotlin.test.assertEquals

internal class AnyPatternTest {
    @Test
    fun `should match multiple patterns`() {
        val pattern = AnyPattern(listOf(NumberTypePattern(), StringPattern()))
        val string = StringValue("hello")
        val number = NumberValue(10)

        string shouldMatch pattern
        number shouldMatch pattern
    }

    @Test
    fun `should create a new pattern based on the given row`() {
        val pattern = AnyPattern(listOf(parsedPattern("""{"id": "(number)"}""")))
        val row = Row(listOf("id"), listOf("10"))

        val value = pattern.newBasedOn(row, Resolver()).first().generate(Resolver())

        if(value is JSONObjectValue) {
            val id = value.jsonObject.getValue("id")

            if(id is NumberValue)
                assertEquals(10, id.number)
            else fail("Expected NumberValue")
        } else fail("Expected JSONObjectValue")
    }

    @Test
    fun `should generate a value based on the pattern given`() {
        NumberValue(10) shouldMatch AnyPattern(listOf(parsedPattern("(number)")))
    }
}