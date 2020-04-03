package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.shouldMatch
import org.junit.jupiter.api.Test
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
import kotlin.test.assertFalse

internal class JSONArrayPatternTest {
    @Test
    fun `An empty array should match an array matcher`() {
        val value = parsedValue("[]")
        val pattern = parsedPattern("""["(number*)"]""")

        value shouldMatch pattern
    }

    @Test
    fun `An array with the first n elements should not match an array with all the elements`() {
        val value = parsedValue("[1,2]")
        val pattern = parsedPattern("""[1,2,3]""")

        assertFalse(pattern.matches(value, Resolver()).isTrue())

    }

    @Test
    fun `should match the rest even if there are no more elements`() {
        val pattern = JSONArrayPattern(listOf(StringPattern(), RestPattern(NumberTypePattern())))
        val value = JSONArrayValue(listOf(StringValue("hello")))

        value shouldMatch pattern
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch JSONArrayPattern(listOf(StringPattern(), StringPattern()))
    }
}