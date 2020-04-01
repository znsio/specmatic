package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldMatch
import run.qontract.core.value.JSONArrayValue

internal class RestPatternTest {
    @Test
    fun `should match even if there are no elements left`() {
        val pattern = RestPattern(NumberTypePattern())
        val value = JSONArrayValue(emptyList())

        value shouldMatch pattern
    }
}