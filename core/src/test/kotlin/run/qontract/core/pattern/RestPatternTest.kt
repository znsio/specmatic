package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.NullValue

internal class RestPatternTest {
    @Test
    fun `should match even if there are no elements left`() {
        val pattern = RestPattern(NumberPattern)
        val value = JSONArrayValue(emptyList())

        value shouldMatch pattern
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch RestPattern(NumberPattern)
    }
}
