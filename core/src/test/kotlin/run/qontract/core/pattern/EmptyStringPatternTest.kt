package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.value.EmptyString
import run.qontract.shouldMatch

internal class EmptyStringPatternTest {
    @Test
    fun `should match empty strings gracefully`() {
        EmptyString shouldMatch EmptyStringPattern
    }
}