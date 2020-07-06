package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.value.NullValue
import run.qontract.core.value.NumberValue
import run.qontract.shouldNotMatch

internal class ExactValuePatternTest {
    @Test
    fun `should match nulls gracefully`() {
        NullValue shouldNotMatch ExactValuePattern(NumberValue(10))
    }
}