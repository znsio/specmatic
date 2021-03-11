package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.resultReport
import run.qontract.core.utilities.withNullPattern
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.emptyPattern
import run.qontract.shouldMatch
import kotlin.test.assertEquals

internal class AnyPatternTest {
    @Test
    fun `should match multiple patterns`() {
        val pattern = AnyPattern(listOf(NumberPattern, StringPattern))
        val string = StringValue("hello")
        val number = NumberValue(10)

        string shouldMatch pattern
        number shouldMatch pattern
    }

    @Test
    fun `error message when a json object does not match nullable primitive such as string in the contract`() {
        val pattern1 = AnyPattern(listOf(NullPattern, StringPattern))
        val pattern2 = AnyPattern(listOf(DeferredPattern("(empty)"), StringPattern))

        val value = parsedValue("""{"firstname": "Jane", "lastname": "Doe"}""")

        val resolver = withNullPattern(Resolver())

        val result1 = pattern1.matches(value, resolver)
        val result2 = pattern2.matches(value, resolver)

        assertThat(resultReport(result2)).isEqualTo("""Expected string, actual was json object: {
    "firstname": "Jane",
    "lastname": "Doe"
}""")

        assertThat(resultReport(result1)).isEqualTo("""Expected string, actual was json object: {
    "firstname": "Jane",
    "lastname": "Doe"
}""")
    }

    @Test
    fun `typename of a nullable type`() {
        val pattern1 = AnyPattern(listOf(NullPattern, StringPattern))
        val pattern2 = AnyPattern(listOf(DeferredPattern("(empty)"), StringPattern))

        assertThat(pattern1.typeName).isEqualTo("(string?)")
        assertThat(pattern2.typeName).isEqualTo("(string?)")
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
        assertThat(AnyPattern(listOf(NullPattern, StringPattern)).encompasses(NullPattern, Resolver(), Resolver())).isInstanceOf(
            Result.Success::class.java)
    }

    @Test
    fun `should encompass any of the specified types`() {
        val bigger = parsedPattern("""(string?)""")
        val smallerString = StringPattern
        val smallerNull = emptyPattern()

        assertThat(bigger.encompasses(smallerString, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smallerNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass itself`() {
        val bigger = parsedPattern("""(string?)""")
        assertThat(bigger.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass another any with fewer types`() {
        val bigger = parsedPattern("""(string?)""")
        val anyOfString = AnyPattern(listOf(StringPattern))

        assertThat(bigger.encompasses(anyOfString, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass itself with a list type`() {
        val bigger = parsedPattern("""(string*?)""")
        assertThat(bigger.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass a matching string list type`() {
        val bigger = parsedPattern("""(string*?)""")
        val smallerStringList = parsedPattern("(string*)")
        assertThat(bigger.encompasses(smallerStringList, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass a matching null type`() {
        val bigger = parsedPattern("""(string*?)""")
        val smallerNull = emptyPattern()
        assertThat(bigger.encompasses(smallerNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `typeName should show nullable when one of the types is null`() {
        val type = AnyPattern(listOf(NullPattern, NumberPattern))
        assertThat(type.typeName).isEqualTo("(number?)")
    }
}
