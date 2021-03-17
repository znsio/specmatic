package `in`.specmatic.core.pattern

import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch

internal class RestPatternTest {
    @Test
    fun `should match even if there are no elements left`() {
        val pattern = RestPattern(NumberPattern)
        val value = JSONArrayValue(emptyList())

        value shouldMatch pattern
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch RestPattern(NumberPattern)
    }
}
