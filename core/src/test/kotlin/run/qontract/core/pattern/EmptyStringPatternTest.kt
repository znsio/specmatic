package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.value.NullValue
import run.qontract.shouldMatch

internal class EmptyStringPatternTest {
    @Test
    fun `should match nulls gracefully`() {
        NullValue shouldMatch EmptyStringPattern
    }
}