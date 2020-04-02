package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.NullValue

internal class StringPatternTest {
    @Test
    fun `should fail to match null values gracefully`() {
        NullValue shouldNotMatch StringPattern()
    }
}