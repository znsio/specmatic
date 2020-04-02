package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.NullValue
import run.qontract.core.value.NumberValue

internal class ExactMatchPatternTest {
    @Test
    fun `should match nulls gracefully`() {
        NullValue shouldNotMatch ExactMatchPattern(NumberValue(10))
    }
}