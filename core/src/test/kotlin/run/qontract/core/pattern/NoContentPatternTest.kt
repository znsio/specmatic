package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldMatch
import run.qontract.core.value.NullValue

internal class NoContentPatternTest {
    @Test
    fun `should match nulls gracefully`() {
        NullValue shouldMatch NoContentPattern()
    }
}