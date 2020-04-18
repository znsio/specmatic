package run.qontract.core.pattern

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.NullValue

internal class ListPatternTest {
    @Test
    fun `should generate a list of patterns each of which is a list pattern`() {
        val patterns = ListPattern(NumberTypePattern).newBasedOn(Row(), Resolver())

        for(pattern in patterns) {
            assertTrue(pattern is ListPattern)
        }
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch ListPattern(StringPattern)
    }
}