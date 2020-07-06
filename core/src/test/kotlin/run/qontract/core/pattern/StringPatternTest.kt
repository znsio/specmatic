package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.value.NullValue
import run.qontract.shouldNotMatch

internal class StringPatternTest {
    @Test
    fun `should fail to match null values gracefully`() {
        NullValue shouldNotMatch StringPattern
    }
}