package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.NullValue

internal class BooleanPatternTest {
    @Test
    fun `should fail to match nulls graceully`() {
        NullValue shouldNotMatch BooleanPattern()
    }
}