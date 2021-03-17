package `in`.specmatic.core.pattern

import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.shouldNotMatch

internal class BooleanPatternTest {
    @Test
    fun `should fail to match nulls graceully`() {
        NullValue shouldNotMatch BooleanPattern
    }
}