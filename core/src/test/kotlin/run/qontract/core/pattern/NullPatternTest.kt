package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
import run.qontract.shouldMatch
import kotlin.test.assertEquals

internal class NullPatternTest {
    @Test
    fun `should match null value`() {
        val nullValue = NullValue
        val pattern = NullPattern

        nullValue shouldMatch pattern
    }

    @Test
    fun `should match empty string`() {
        val emptyString = StringValue("")
        val pattern = NullPattern

        emptyString shouldMatch pattern
    }

    @Test
    fun `should generate null value`() {
        assertEquals(NullValue,  NullPattern.generate(Resolver()))
    }

    @Test
    fun `should create a new array of patterns containing itself`() {
        assertEquals(listOf(NullPattern), NullPattern.newBasedOn(Row(), Resolver()))
    }
}