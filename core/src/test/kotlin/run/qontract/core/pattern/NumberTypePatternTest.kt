package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.NullValue

internal class NumberTypePatternTest {
    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch NumberTypePattern
    }
}