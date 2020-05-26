package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.shouldMatch
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import kotlin.test.assertEquals

internal class AnyPatternTest {
    @Test
    fun `should match multiple patterns`() {
        val pattern = AnyPattern(listOf(NumberTypePattern, StringPattern))
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

    @Test
    fun `AnyPattern of null and string patterns should encompass null pattern`() {
        assertThat(AnyPattern(listOf(NullPattern, StringPattern)).encompasses2(NullPattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass any of the specified types`() {
        val bigger = parsedPattern("""(string?)""")
        val smallerString = StringPattern
        val smallerNull = NullPattern

        assertThat(bigger.encompasses2(smallerString, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses2(smallerNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass itself`() {
        val bigger = parsedPattern("""(string?)""")
        assertThat(bigger.encompasses2(bigger, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass another any with fewer types`() {
        val bigger = parsedPattern("""(string?)""")
        val anyOfString = AnyPattern(listOf(StringPattern))

        assertThat(bigger.encompasses2(anyOfString, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass itself with a list type`() {
        val bigger = parsedPattern("""(string*?)""")
        assertThat(bigger.encompasses2(bigger, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass a matching string list type`() {
        val bigger = parsedPattern("""(string*?)""")
        val smallerStringList = parsedPattern("(string*)")
        assertThat(bigger.encompasses2(smallerStringList, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass a matching null type`() {
        val bigger = parsedPattern("""(string*?)""")
        val smallerNull = NullPattern
        assertThat(bigger.encompasses2(smallerNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }
}
