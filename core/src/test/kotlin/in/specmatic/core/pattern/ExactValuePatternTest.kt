package `in`.specmatic.core.pattern

import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.shouldNotMatch

internal class ExactValuePatternTest {
    @Test
    fun `should match nulls gracefully`() {
        NullValue shouldNotMatch ExactValuePattern(NumberValue(10))
    }
}